package com.meridiangoods.recordpaymentresult

import org.axonframework.eventsourcing.annotation.EventTag
import java.time.Instant
import java.util.UUID

/**
 * Event: Payment Captured. See `slices/record-payment-result.md`.
 *
 * INV-RPR-1 (anti-corruption seam): every field here is one of our own typed, named fields —
 * the provider's raw webhook payload never reaches this class. See
 * [PaymentCaptureWebhookMapper] for the boundary mapping and [PaymentProviderCapturePayload]
 * for the (deliberately different-shaped) wire format it maps from.
 *
 * Tagged with `paymentId` (this slice's own idempotency entity) and `orderId` (for downstream
 * readers — `Open Orders`, `Order Status` — per the doc's Dependencies section).
 */
data class PaymentCaptured(
    @EventTag(key = "paymentId") val paymentId: UUID,
    @EventTag(key = "orderId") val orderId: UUID,
    val amountCents: Int,
    val capturedAt: Instant,
    val providerRef: String,
)
