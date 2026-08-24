package com.meridiangoods.requestpayment

import com.meridiangoods.paymentstorequest.PaymentsToRequestRepository
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
 * Event-triggered by `Order Placed` (Pattern A, `axon-policy-patterns`), but the *decision* to
 * act is made by consulting the `Payments To Request` to-do list
 * ([PaymentsToRequestRepository]), not by trusting the event payload directly: `react` looks the
 * order up in the queue and only issues `Request Payment` when an entry is actually present
 * there. This is the mechanism the doc's Trigger & Actor section describes ("watches the
 * `Payments To Request` view ... for each order it finds there, issues the `Request Payment`
 * command") and what INV-RP-1's exactly-once explanation relies on: once `Payment Requested`
 * lands, `INV-PTR-2` removes the order from the queue, so a later reaction to the same or a
 * redelivered `Order Placed` finds nothing to select and does not act.
 *
 * `amountCents` is taken from the queue entry's own `totalCents` (INV-RP-2), never from the
 * triggering event — the queue entry, not the event, is this automation's source of truth for
 * the amount.
 *
 * `Request Payment`'s own command handler ([RequestPaymentCommandHandler], same slice) still
 * enforces INV-RP-1 a second, independent way via its own event-sourced `State` keyed by
 * `orderId` (INV-RP-3: this class holds a `CommandDispatcher`, never an `EventAppender`, so it
 * cannot append `Payment Requested` directly — that command handler is the only place it does).
 */
class WhenOrderPlacedThenRequestPaymentFromQueue(
    private val paymentsToRequest: PaymentsToRequestRepository,
    private val clock: Clock = Clock.systemUTC(),
) {

    @EventHandler
    fun react(event: OrderPlaced, commandDispatcher: CommandDispatcher): CompletableFuture<*> {
        // INV-RP-1 (exactly-once liveness) / INV-RP-2 (amount fidelity): the queue, not the
        // event, decides whether to act and what amount to request. An order absent from the
        // queue (already drained by a prior Payment Requested, or never queued) is not acted on.
        val entry = paymentsToRequest.findByOrderId(event.orderId) ?: return CompletableFuture.completedFuture(null)

        return commandDispatcher.send(
            RequestPayment(
                orderId = entry.orderId,
                paymentId = UUID.randomUUID(),
                amountCents = entry.totalCents,
                requestedAt = Instant.now(clock),
            ),
            Any::class.java,
        )
    }
}
