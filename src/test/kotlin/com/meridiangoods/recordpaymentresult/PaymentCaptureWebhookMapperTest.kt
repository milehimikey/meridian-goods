package com.meridiangoods.recordpaymentresult

import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Plain unit tests for [PaymentCaptureWebhookMapper] — no Axon fixture involved, per
 * `axon-translation-patterns`' guidance for testing an externally-triggered translation's
 * mapping step in isolation. Covers the doc's "Malformed/partial provider payload" alternate
 * flow, one failure mode per test.
 */
class PaymentCaptureWebhookMapperTest {

    private val paymentId = UUID.randomUUID()
    private val orderId = UUID.randomUUID()

    private fun validPayload(providerTransactionId: String = "ch_abc123") = PaymentProviderCapturePayload(
        paymentReference = paymentId.toString(),
        merchantOrderId = orderId.toString(),
        capturedAmount = "50.00",
        capturedAtIso = "2026-08-20T11:00:00Z",
        providerTransactionId = providerTransactionId,
    )

    @Test
    fun `given a well-formed provider payload, when mapped, then Record Payment Result carries the translated fields`() {
        val command = PaymentCaptureWebhookMapper.toCommand(validPayload())

        assertEquals(paymentId, command?.paymentId)
        assertEquals(orderId, command?.orderId)
        assertEquals(5000, command?.amountCents)
        assertEquals(Instant.parse("2026-08-20T11:00:00Z"), command?.capturedAt)
        assertEquals("ch_abc123", command?.providerRef)
    }

    @Test
    fun `given a non-UUID paymentReference, when mapped, then null (rejected at the boundary)`() {
        val payload = validPayload().copy(paymentReference = "not-a-uuid")
        assertNull(PaymentCaptureWebhookMapper.toCommand(payload))
    }

    @Test
    fun `given a non-UUID merchantOrderId, when mapped, then null (rejected at the boundary)`() {
        val payload = validPayload().copy(merchantOrderId = "not-a-uuid")
        assertNull(PaymentCaptureWebhookMapper.toCommand(payload))
    }

    @Test
    fun `given a non-numeric capturedAmount, when mapped, then null (rejected at the boundary)`() {
        val payload = validPayload().copy(capturedAmount = "fifty dollars")
        assertNull(PaymentCaptureWebhookMapper.toCommand(payload))
    }

    @Test
    fun `given an amount with more than two decimal places, when mapped, then null (rejected at the boundary)`() {
        val payload = validPayload().copy(capturedAmount = "50.005")
        assertNull(PaymentCaptureWebhookMapper.toCommand(payload))
    }

    @Test
    fun `given a malformed capturedAtIso, when mapped, then null (rejected at the boundary)`() {
        val payload = validPayload().copy(capturedAtIso = "not-a-timestamp")
        assertNull(PaymentCaptureWebhookMapper.toCommand(payload))
    }

    @Test
    fun `given a blank providerTransactionId, when mapped, then null (rejected at the boundary)`() {
        val payload = validPayload(providerTransactionId = "   ")
        assertNull(PaymentCaptureWebhookMapper.toCommand(payload))
    }
}
