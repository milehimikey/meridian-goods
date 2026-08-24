package com.meridiangoods.recordpaymentresult

import com.meridiangoods.requestpayment.PaymentRequested
import org.axonframework.eventsourcing.annotation.EventSourcedEntity
import org.axonframework.eventsourcing.annotation.EventSourcingHandler
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator
import org.axonframework.messaging.commandhandling.annotation.CommandHandler
import org.axonframework.messaging.eventhandling.gateway.EventAppender
import org.axonframework.modelling.annotation.InjectEntity
import java.util.UUID

/**
 * Command handler for `Record Payment Result` (`slices/record-payment-result.md`). Decider
 * idiom: [decide] is a pure function of `(command, state) -> List<PaymentCaptured>`; [handle]
 * wires it to Axon.
 */
class RecordPaymentResultCommandHandler {

    @CommandHandler
    fun handle(
        command: RecordPaymentResult,
        @InjectEntity(idProperty = "paymentId") state: PaymentState?,
        eventAppender: EventAppender,
    ) {
        eventAppender.append(decide(command, state ?: PaymentState()))
    }

    /**
     * Pure decision function — no side effects. Every branch is traceable to an `INV-RPR-n` in
     * `slices/record-payment-result.md`.
     */
    private fun decide(command: RecordPaymentResult, state: PaymentState): List<PaymentCaptured> {
        // INV-RPR-3 (unknown paymentId is a rejection, not a fact): an event-sourced check —
        // no Payment Requested event has ever been folded into this paymentId's state — rejects
        // outright, before any idempotency/no-op consideration below applies.
        check(state.requested) {
            "INV-RPR-3: no Payment Requested exists for paymentId ${command.paymentId}; a " +
                "capture result for a payment we never requested is not a fact about our system"
        }

        // Command/Input table rule: orderId must match the orderId already associated with
        // paymentId via Payment Requested (not itself a numbered INV, but a real field-level
        // validation rule from the doc).
        check(state.orderId == command.orderId) {
            "orderId ${command.orderId} does not match the orderId ${state.orderId} already " +
                "associated with paymentId ${command.paymentId} via Payment Requested"
        }

        if (state.captured) {
            // "Distinct providerRef is a distinct fact" scenario: a redelivery of the SAME
            // capture (identical providerRef) is INV-RPR-2's ordinary idempotent no-op — the
            // provider is just retelling us something we already recorded. A DIFFERENT
            // providerRef arriving for an already-captured payment is not a redelivery, it is a
            // second, distinct capture fact for a payment this domain only ever captures once —
            // that anomaly is rejected outright, loudly, not silently absorbed into the same
            // no-op branch as an ordinary retry.
            if (state.providerRef != command.providerRef) {
                error(
                    "distinct providerRef on an already-captured payment: paymentId " +
                        "${command.paymentId} was already captured with providerRef " +
                        "'${state.providerRef}', but this Record Payment Result carries a " +
                        "different providerRef '${command.providerRef}' — a payment is captured " +
                        "at most once, so a genuinely distinct capture fact for an " +
                        "already-captured payment is rejected, not treated as a redelivery",
                )
            }
            return emptyList()
        }

        return listOf(
            PaymentCaptured(
                paymentId = command.paymentId,
                orderId = command.orderId,
                amountCents = command.amountCents,
                capturedAt = command.capturedAt,
                providerRef = command.providerRef,
            ),
        )
    }

    // Mutable style — see place-order's PlaceOrderCommandHandler for why a genuinely zero-arg
    // @EntityCreator constructor is required against Axon Framework 5.3.1's real reflection
    // behavior.
    @EventSourcedEntity(tagKey = "paymentId")
    class PaymentState @EntityCreator constructor() {
        var requested: Boolean = false
            private set
        var orderId: UUID? = null
            private set
        var captured: Boolean = false
            private set
        var providerRef: String? = null
            private set

        @EventSourcingHandler
        fun evolve(event: PaymentRequested) {
            requested = true
            orderId = event.orderId
        }

        @EventSourcingHandler
        fun evolve(event: PaymentCaptured) {
            captured = true
            providerRef = event.providerRef
        }
    }
}
