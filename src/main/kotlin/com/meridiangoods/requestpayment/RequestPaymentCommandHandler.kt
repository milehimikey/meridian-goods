package com.meridiangoods.requestpayment

import com.meridiangoods.placeorder.OrderPlaced
import org.axonframework.eventsourcing.annotation.EventSourcedEntity
import org.axonframework.eventsourcing.annotation.EventSourcingHandler
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator
import org.axonframework.messaging.commandhandling.annotation.CommandHandler
import org.axonframework.messaging.eventhandling.gateway.EventAppender
import org.axonframework.modelling.annotation.InjectEntity
import java.util.UUID

/**
 * Command handler for `Request Payment` (`slices/request-payment.md`). Decider idiom: [decide]
 * is a pure function of `(command, state) -> List<PaymentRequested>`; [handle] wires it to Axon.
 *
 * This is the *only* place [PaymentRequested] is appended (INV-RP-3) — see
 * [WhenOrderPlacedThenRequestPayment], which holds a `CommandDispatcher`, never an
 * `EventAppender`, and so cannot append it directly.
 */
class RequestPaymentCommandHandler {

    @CommandHandler
    fun handle(
        command: RequestPayment,
        // Nullable for the same reason as Place Order's own State: an order that has never had
        // a payment requested (or, defensively, never even seen an Order Placed) has no prior
        // entity — see place-order's PlaceOrderCommandHandler for the axon-kotlin
        // KotlinReflectNullabilityResolver note this relies on.
        @InjectEntity(idProperty = "orderId") state: State?,
        eventAppender: EventAppender,
    ) {
        eventAppender.append(decide(command, state ?: State()))
    }

    /**
     * Pure decision function — no side effects. Every branch is traceable to an `INV-RP-n` in
     * `slices/request-payment.md`.
     */
    private fun decide(command: RequestPayment, state: State): List<PaymentRequested> {
        // INV-RP-1 (exactly-once liveness): once a Payment Requested event has landed for this
        // orderId, any further Request Payment for the same order — whether a genuine re-poll
        // by a confused processor, a redelivered Order Placed causing a second reaction, or a
        // direct retry — is a structural no-op. This is the entity-sourced enforcement that
        // stands in for the doc's read-side to-do-list draining (see the judgment call recorded
        // on WhenOrderPlacedThenRequestPayment and in the findings file): the *outcome* — at
        // most one Payment Requested per orderId — is identical either way.
        if (state.requested) {
            return emptyList()
        }

        // INV-RP-2 (amount fidelity): amountCents must equal the order's own totalCents, traced
        // back to Order Placed. Comparing against state.totalCents (sourced directly from this
        // slice's own view of Order Placed, tagged by the same orderId) rather than trusting
        // whatever the caller supplies is what makes this a real check rather than a formality;
        // a never-placed order (state.totalCents == null) can never satisfy it either.
        check(state.totalCents == command.amountCents) {
            "INV-RP-2: amountCents ${command.amountCents} does not equal order ${command.orderId}'s " +
                "recorded totalCents (${state.totalCents}) — the processor must be a pure conduit " +
                "for the order's own total, never recomputing or adjusting it"
        }

        return listOf(
            PaymentRequested(
                orderId = command.orderId,
                paymentId = command.paymentId,
                amountCents = command.amountCents,
                requestedAt = command.requestedAt,
            ),
        )
    }

    // Mutable style, matching place-order's own State — see PlaceOrderCommandHandler's comment
    // for why a genuinely zero-arg @EntityCreator constructor is required against Axon
    // Framework 5.3.1's real reflection behavior.
    @EventSourcedEntity(tagKey = "orderId")
    class State @EntityCreator constructor() {
        /** Sourced from this order's own `Order Placed` — `null` if the order was never placed. */
        var totalCents: Int? = null
            private set
        var requested: Boolean = false
            private set

        @EventSourcingHandler
        fun evolve(event: OrderPlaced) {
            totalCents = event.totalCents
        }

        @EventSourcingHandler
        fun evolve(event: PaymentRequested) {
            requested = true
        }
    }
}
