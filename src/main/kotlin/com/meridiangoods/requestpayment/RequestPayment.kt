package com.meridiangoods.requestpayment

import org.axonframework.modelling.annotation.TargetEntityId
import java.time.Instant
import java.util.UUID

/**
 * Command: Request Payment. See `slices/request-payment.md`.
 *
 * `orderId` is the `@TargetEntityId` — [RequestPaymentCommandHandler.State] is keyed by
 * `orderId`, not `paymentId`, because INV-RP-1's exactly-once guarantee ("at most one
 * `Payment Requested` event per `orderId`") is naturally expressed as a per-order idempotency
 * check. `paymentId` is still carried as an ordinary field: it is the processor's own minted
 * idempotency key for the payment attempt (per the doc), and it is carried onto
 * [PaymentRequested] unchanged.
 */
data class RequestPayment(
    @TargetEntityId val orderId: UUID,
    val paymentId: UUID,
    val amountCents: Int,
    val requestedAt: Instant,
)
