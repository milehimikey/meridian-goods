package com.meridiangoods.recordpaymentresult

/**
 * The payment provider's own webhook wire shape — deliberately *not* our vocabulary. See
 * `slices/record-payment-result.md`'s Command/Input table notes and INV-RPR-1.
 *
 * Field-by-field, this is invented for this demo (no real provider is integrated), but shaped
 * the way the doc describes a real provider payload being shaped: a correlation id rather than
 * our own `paymentId`, a decimal-string amount rather than integer cents, and an ISO-8601
 * capture timestamp string rather than a native `Instant` — exactly the kind of shape
 * [PaymentCaptureWebhookMapper] exists to translate away from.
 */
data class PaymentProviderCapturePayload(
    /** The provider's correlation id for *our* original payment request — maps to `paymentId`. */
    val paymentReference: String,
    /** Maps to `orderId`. */
    val merchantOrderId: String,
    /** Decimal string, e.g. `"50.00"` — maps to `amountCents` (`5000`). */
    val capturedAmount: String,
    /** ISO-8601 instant string — maps to `capturedAt`. */
    val capturedAtIso: String,
    /** The provider's own identifier for this capture — maps to `providerRef` (INV-RPR-2's key). */
    val providerTransactionId: String,
)
