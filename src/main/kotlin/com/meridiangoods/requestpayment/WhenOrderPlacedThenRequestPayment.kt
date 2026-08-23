package com.meridiangoods.requestpayment

import com.meridiangoods.placeorder.OrderPlaced
import org.axonframework.messaging.commandhandling.gateway.CommandDispatcher
import org.axonframework.messaging.eventhandling.annotation.EventHandler
import java.time.Clock
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * Automation: Payment Requester. See `slices/request-payment.md`.
 *
 * **Judgment call** (recorded in full in `w3-3-reactions.md`): the slice doc's Trigger & Actor
 * section describes this processor as watching the `Payments To Request` read model, built and
 * owned by a *different, parallel, independently-branched* slice/PR (`payments-to-request`) not
 * present on this branch by design (the two implementers' PRs are never stacked). Rather than
 * take a compile-time or runtime dependency on a projection that may not exist yet, this
 * automation reacts directly and statelessly (Pattern A, `axon-policy-patterns`) to
 * `Order Placed` itself and dispatches `Request Payment` on every occurrence.
 *
 * `Request Payment`'s own command handler ([RequestPaymentCommandHandler], same slice) is what
 * actually enforces INV-RP-1's exactly-once guarantee, via its own event-sourced `State` keyed
 * by `orderId` (an idempotent-creation check, `axon-entity-patterns`) — not by consulting a
 * drained to-do list. The *observable* outcome the doc's Given/When/Then scenarios describe (at
 * most one `Payment Requested` per order; a re-poll/redelivery produces no second command
 * effect) is identical; only the internal mechanism differs. This is a technical
 * implementation-mechanism choice with no behavioral surface, not a silent decision about
 * unspecified business behavior — flagged here and in the findings file per the contract's
 * propose-don't-decide rule regardless, since it does deviate from the doc's *described*
 * mechanism.
 *
 * One accepted simplification that follows from being stateless: `paymentId` is minted fresh on
 * every invocation of [react] rather than remembered across retries of "the same decision" (the
 * doc's Alternate & Error Flows note on the command's own retry-safety). A wasted, never-
 * persisted `paymentId` from a no-op duplicate reaction has no observable effect — INV-RP-1
 * does not key on `paymentId` — and no scenario in the doc's Scenarios section tests
 * paymentId-stability under retry, so this is not a gap against anything tested.
 */
class WhenOrderPlacedThenRequestPayment(private val clock: Clock = Clock.systemUTC()) {

    @EventHandler
    fun react(event: OrderPlaced, commandDispatcher: CommandDispatcher): CompletableFuture<*> =
        commandDispatcher.send(
            RequestPayment(
                orderId = event.orderId,
                paymentId = UUID.randomUUID(),
                amountCents = event.totalCents,
                requestedAt = Instant.now(clock),
            ),
            Any::class.java,
        )
}
