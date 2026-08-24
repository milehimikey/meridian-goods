package com.meridiangoods.cancelorder

import com.meridiangoods.placeorder.OrderPlaced
import com.meridiangoods.recordpaymentresult.PaymentCaptured
import org.axonframework.eventsourcing.annotation.EventCriteriaBuilder
import org.axonframework.eventsourcing.annotation.EventSourcedEntity
import org.axonframework.eventsourcing.annotation.EventSourcingHandler
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator
import org.axonframework.messaging.commandhandling.annotation.CommandHandler
import org.axonframework.messaging.eventhandling.gateway.EventAppender
import org.axonframework.messaging.eventstreaming.EventCriteria
import org.axonframework.messaging.eventstreaming.Tag
import org.axonframework.modelling.annotation.InjectEntity
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Command handler for `Cancel Order` (`slices/cancel-order.md`). Decider idiom: [decide] is a
 * pure function of `(command, state, cancelledAt) -> List<OrderCancelled>`; [handle] wires it to
 * Axon.
 *
 * ## DCB criteria design (INV-CO-1's cross-entity question)
 *
 * INV-CO-1 needs this decision to see `Payment Captured` — an event authored by a *different*
 * slice (`com.meridiangoods.recordpaymentresult`) — for the *same order*, not just this slice's
 * own `Order Cancelled` history. There's no second "party" here in the `axon-dcb-modeling` sense
 * (no second id, no `either(...)` union of two tags) — it's one party, the order, whose event
 * stream happens to be authored by three different command handlers across the model's timeline
 * (`Order Placed` from `place-order`, `Payment Captured` from `record-payment-result`,
 * `Order Cancelled` from this slice). All three already tag `orderId` with the same key. So the
 * deliberate design here is a **single-tag, multi-type** criteria: one `havingTags(orderId)`
 * scoped narrowly to exactly the three event types this decision needs via `andBeingOneOfTypes`
 * — not the plain `@EventSourcedEntity(tagKey = "orderId")` shorthand (which would implicitly
 * match *every* event ever tagged `orderId`, present or future, per `axon-dcb-modeling`'s
 * "always constrain types" guidance) and not a two-branch `either(...)` (there is only one tag
 * here, not two parties to union).
 */
class CancelOrderCommandHandler(
    private val clock: Clock = Clock.systemUTC(),
) {

    companion object {
        // Support-requested: matches the provider's auto-void settlement window.
        val CANCELLATION_GRACE: Duration = Duration.ofHours(24)
    }

    @CommandHandler
    fun handle(
        command: CancelOrder,
        // Nullable: an unknown orderId (no Order Placed ever recorded) is a real, tested
        // rejection case (INV: "Rejected - unknown order"), not a create-or-noop shape like
        // Place Order's. The nullable type routes @InjectEntity to RESOLVE_NULL via axon-kotlin's
        // KotlinReflectNullabilityResolver (see PlaceOrderCommandHandler's doc comment / the
        // scaffold findings file for why this dependency is required at all).
        @InjectEntity(idProperty = "orderId") state: State?,
        eventAppender: EventAppender,
    ) {
        eventAppender.append(decide(command, state, Instant.now(clock)))
    }

    /**
     * Pure decision function — no side effects. Every branch is traceable to one `INV-CO-n` (or
     * a named rejection scenario) in `slices/cancel-order.md`.
     */
    private fun decide(command: CancelOrder, state: State?, cancelledAt: Instant): List<OrderCancelled> {
        // "Rejected - unknown order": no Order Placed has ever been recorded for this orderId.
        checkNotNull(state) {
            "unknown order: no Order Placed recorded for orderId ${command.orderId}"
        }
        check(state.placed) {
            "unknown order: no Order Placed recorded for orderId ${command.orderId}"
        }

        // "Rejected - wrong customer": only the order's own owner may cancel it. Checked before
        // the idempotency short-circuit below so an unauthorized caller never learns the order's
        // cancellation status from this command's outcome.
        check(state.customerId == command.customerId) {
            "unauthorized: customerId ${command.customerId} does not own orderId ${command.orderId}"
        }

        // INV-CO-2: idempotent cancel. Already-cancelled orderId is a silent no-op that returns
        // the original cancellation's result — the original cancelledAt stands, never overwritten
        // by a later duplicate click's timestamp. No second event is appended.
        if (state.cancelled) {
            return emptyList()
        }

        // INV-CO-1, with the grace window support asked for: customers may still self-cancel
        // shortly after capture (the provider auto-voids inside its settlement window, so no
        // refund flow is needed for these).
        val capturedAt = state.paymentCapturedAt
        check(capturedAt == null || Duration.between(capturedAt, cancelledAt) <= CANCELLATION_GRACE) {
            "INV-CO-1: orderId ${command.orderId} cannot be cancelled - payment captured more than ${CANCELLATION_GRACE.toHours()}h ago"
        }

        return listOf(
            OrderCancelled(
                orderId = command.orderId,
                customerId = command.customerId,
                cancelledAt = cancelledAt,
            ),
        )
    }

    // Mutable style, not the immutable data-class-plus-copy() style axon-entity-patterns
    // recommends by default: matches PlaceOrderCommandHandler.State's own note — a data class
    // @EntityCreator constructor with several defaulted parameters fails at runtime against real
    // axon-eventsourcing 5.3.1 (AnnotationBasedEventSourcedEntityFactory resolves every declared
    // constructor parameter as an injectable "resource", not a defaultable argument). A genuinely
    // zero-arg @EntityCreator constructor is required.
    @EventSourcedEntity
    class State @EntityCreator constructor() {
        var placed: Boolean = false
            private set
        var customerId: UUID? = null
            private set
        var paymentCaptured: Boolean = false
            private set
        var paymentCapturedAt: Instant? = null
            private set
        var cancelled: Boolean = false
            private set

        @EventSourcingHandler
        fun evolve(event: OrderPlaced) {
            placed = true
            customerId = event.customerId
        }

        @EventSourcingHandler
        fun evolve(event: PaymentCaptured) {
            paymentCaptured = true
            paymentCapturedAt = event.capturedAt
        }

        @EventSourcingHandler
        fun evolve(event: OrderCancelled) {
            cancelled = true
        }

        companion object {
            /**
             * The deliberate DCB criteria this class's doc comment above explains: one tag
             * (`orderId`), narrowed to exactly the three event types this decision needs to see.
             */
            @EventCriteriaBuilder
            @JvmStatic
            private fun resolveCriteria(orderId: UUID): EventCriteria =
                EventCriteria
                    .havingTags(Tag.of("orderId", orderId.toString()))
                    .andBeingOneOfTypes(
                        OrderPlaced::class.java.name,
                        PaymentCaptured::class.java.name,
                        OrderCancelled::class.java.name,
                    )
        }
    }
}
