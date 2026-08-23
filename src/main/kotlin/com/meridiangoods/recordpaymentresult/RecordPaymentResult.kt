package com.meridiangoods.recordpaymentresult

import org.axonframework.modelling.annotation.TargetEntityId
import java.time.Instant
import java.util.UUID

/**
 * Command: Record Payment Result. See `slices/record-payment-result.md`.
 *
 * `paymentId` is the `@TargetEntityId` — [RecordPaymentResultCommandHandler.PaymentState] is
 * keyed by `paymentId`, matching INV-RPR-3's own framing ("no `Payment Requested` event Meridian
 * Goods has recorded" for this `paymentId`) and INV-RPR-2's idempotency scope (per-payment, not
 * per-order — a payment is captured at most once).
 */
data class RecordPaymentResult(
    @TargetEntityId val paymentId: UUID,
    val orderId: UUID,
    val amountCents: Int,
    val capturedAt: Instant,
    val providerRef: String,
)
