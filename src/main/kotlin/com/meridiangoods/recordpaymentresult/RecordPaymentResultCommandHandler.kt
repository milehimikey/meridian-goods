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

        // INV-RPR-2 (idempotent under retries) and the "distinct providerRef is a distinct
        // fact" scenario: once this payment is captured, no further Record Payment Result for
        // the same paymentId produces a new event — whether the retry carries the identical
        // providerRef (a harmless webhook redelivery) or a different one (this domain doesn't
        // allow a second capture per payment either way; the doc resolves this the same way).
        if (state.captured) {
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

        @EventSourcingHandler
        fun evolve(event: PaymentRequested) {
            requested = true
            orderId = event.orderId
        }

        @EventSourcingHandler
        fun evolve(event: PaymentCaptured) {
            captured = true
        }
    }
}
