package com.meridiangoods.recordpaymentresult

import com.meridiangoods.requestpayment.PaymentRequested
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer
import org.axonframework.test.fixture.AxonTestFixture
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertTrue

/**
 * Synchronous, `given().when().command().then()` tests for `RecordPaymentResultCommandHandler`'s
 * decision logic (`slices/record-payment-result.md`). See
 * [PaymentProviderAdapterIntegrationTest] for the slower end-to-end proof that a webhook payload
 * actually reaches this command handler through [PaymentProviderAdapter] and
 * [PaymentCaptureWebhookMapper] — including the two scenarios the task explicitly calls out
 * (webhook redelivery no-op, unknown-paymentId rejection) exercised at the translation boundary
 * itself, not just here at the command-handler level.
 */
class RecordPaymentResultCommandHandlerTest {

    private lateinit var fixture: AxonTestFixture

    private val paymentId = UUID.randomUUID()
    private val orderId = UUID.randomUUID()
    private val requestedAt: Instant = Instant.parse("2026-08-20T10:05:00Z")
    private val capturedAt: Instant = Instant.parse("2026-08-20T11:00:00Z")

    @BeforeEach
    fun beforeEach() {
        val configurer = RecordPaymentResultConfiguration().configure(EventSourcingConfigurer.create())
        fixture = AxonTestFixture.with(configurer)
    }

    @AfterEach
    fun afterEach() {
        fixture.stop()
    }

    private fun paymentRequested() = PaymentRequested(orderId, paymentId, amountCents = 5000, requestedAt = requestedAt)

    // --- Happy path -----------------------------------------------------------------------

    @Test
    fun `happy path - given Payment Requested for P1, when Record Payment Result, then Payment Captured is recorded`() {
        fixture.given()
            .event(paymentRequested())
            .`when`()
            .command(RecordPaymentResult(paymentId, orderId, amountCents = 5000, capturedAt = capturedAt, providerRef = "ch_abc123"))
            .then()
            .success()
            .eventsSatisfy { events ->
                assertTrue(events.size == 1)
                val event = events.single().payload() as PaymentCaptured
                assertTrue(event.paymentId == paymentId)
                assertTrue(event.orderId == orderId)
                assertTrue(event.amountCents == 5000)
                assertTrue(event.capturedAt == capturedAt)
                assertTrue(event.providerRef == "ch_abc123")
            }
    }

    // --- INV-RPR-3: unknown paymentId is a rejection, not a fact --------------------------------

    @Test
    fun `INV-RPR-3 - given no Payment Requested exists for P9, when Record Payment Result, then rejected`() {
        fixture.given()
            .`when`()
            .command(RecordPaymentResult(paymentId, orderId, amountCents = 5000, capturedAt = capturedAt, providerRef = "ch_abc123"))
            .then()
            .exceptionSatisfies { exception ->
                assertTrue(exception.message?.contains("INV-RPR-3") == true)
            }
    }

    @Test
    fun `given orderId mismatched against the one already associated with paymentId, when Record Payment Result, then rejected`() {
        fixture.given()
            .event(paymentRequested())
            .`when`()
            .command(RecordPaymentResult(paymentId, UUID.randomUUID(), amountCents = 5000, capturedAt = capturedAt, providerRef = "ch_abc123"))
            .then()
            .exceptionSatisfies { exception ->
                assertTrue(exception.message?.contains("does not match the orderId") == true)
            }
    }

    // --- INV-RPR-2: idempotent under retries -------------------------------------------------

    @Test
    fun `INV-RPR-2 - given Payment Captured already recorded with providerRef ch_abc123, when redelivered with the same providerRef, then no second event is recorded`() {
        val alreadyCaptured = PaymentCaptured(paymentId, orderId, amountCents = 5000, capturedAt = capturedAt, providerRef = "ch_abc123")

        fixture.given()
            .event(paymentRequested())
            .event(alreadyCaptured)
            .`when`()
            .command(RecordPaymentResult(paymentId, orderId, amountCents = 5000, capturedAt = capturedAt, providerRef = "ch_abc123"))
            .then()
            .success()
            .noEvents()
    }

    @Test
    fun `distinct providerRef is a distinct fact - given Payment Captured already recorded with providerRef ch_abc123, when a different providerRef arrives, then rejected`() {
        val alreadyCaptured = PaymentCaptured(paymentId, orderId, amountCents = 5000, capturedAt = capturedAt, providerRef = "ch_abc123")

        fixture.given()
            .event(paymentRequested())
            .event(alreadyCaptured)
            .`when`()
            .command(RecordPaymentResult(paymentId, orderId, amountCents = 5000, capturedAt = capturedAt.plusSeconds(30), providerRef = "ch_xyz789"))
            .then()
            .exceptionSatisfies { exception ->
                assertTrue(exception.message?.contains("distinct providerRef on an already-captured payment") == true)
                assertTrue(exception.message?.contains("ch_abc123") == true)
                assertTrue(exception.message?.contains("ch_xyz789") == true)
            }
    }

    // --- INV-RPR-1: anti-corruption seam (structural) -----------------------------------------

    @Test
    fun `INV-RPR-1 - Payment Captured has only our own typed fields, no raw provider payload field`() {
        val declaredFieldNames = PaymentCaptured::class.java.declaredFields.map { it.name }.toSet()
        assertTrue(
            declaredFieldNames == setOf("paymentId", "orderId", "amountCents", "capturedAt", "providerRef"),
        )
    }
}
