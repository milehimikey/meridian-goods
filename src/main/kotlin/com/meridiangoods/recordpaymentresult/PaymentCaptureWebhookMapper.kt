package com.meridiangoods.recordpaymentresult

import java.math.RoundingMode
import java.time.Instant
import java.util.UUID

/**
 * Anti-corruption mapper for `Record Payment Result` (`slices/record-payment-result.md`,
 * INV-RPR-1). Maps the provider's wire shape onto our own command — never onto the event
 * directly, and never lets a malformed/partial payload become a command attempt at all (see the
 * doc's "Malformed/partial provider payload" alternate flow): every failure mode returns `null`
 * rather than throwing or guessing a default.
 */
object PaymentCaptureWebhookMapper {

    fun toCommand(payload: PaymentProviderCapturePayload): RecordPaymentResult? {
        val paymentId = payload.paymentReference.toUuidOrNull() ?: return null
        val orderId = payload.merchantOrderId.toUuidOrNull() ?: return null
        val amountCents = payload.capturedAmount.toCentsOrNull() ?: return null
        val capturedAt = payload.capturedAtIso.toInstantOrNull() ?: return null
        val providerRef = payload.providerTransactionId.trim()
        if (providerRef.isEmpty()) return null

        return RecordPaymentResult(
            paymentId = paymentId,
            orderId = orderId,
            amountCents = amountCents,
            capturedAt = capturedAt,
            providerRef = providerRef,
        )
    }

    private fun String.toUuidOrNull(): UUID? = runCatching { UUID.fromString(this) }.getOrNull()

    private fun String.toInstantOrNull(): Instant? = runCatching { Instant.parse(this) }.getOrNull()

    /** Converts a decimal-string currency amount (e.g. `"50.00"`) to integer cents (`5000`). */
    private fun String.toCentsOrNull(): Int? {
        val decimal = toBigDecimalOrNull() ?: return null
        val cents = runCatching { decimal.movePointRight(2).setScale(0, RoundingMode.UNNECESSARY) }
            .getOrNull() ?: return null
        return runCatching { cents.intValueExact() }.getOrNull()
    }
}
