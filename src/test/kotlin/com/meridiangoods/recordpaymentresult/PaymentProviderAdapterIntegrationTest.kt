package com.meridiangoods.recordpaymentresult

import com.meridiangoods.requestpayment.PaymentRequested
import org.axonframework.common.configuration.AxonConfiguration
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer
import org.axonframework.eventsourcing.eventstore.EventStore
import org.axonframework.messaging.commandhandling.gateway.CommandGateway
import org.axonframework.messaging.eventhandling.gateway.EventGateway
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * End-to-end proof that a provider webhook payload really flows through
 * [PaymentProviderAdapter] → [PaymentCaptureWebhookMapper] → `Record Payment Result` → a real
 * `Payment Captured` event — not just that the command handler's decision logic is correct in
 * isolation (see [RecordPaymentResultCommandHandlerTest] for that).
 *
 * Per `axon-translation-patterns`: an externally-triggered translation has no internal
 * triggering event, so `AxonTestFixture`'s `given().events(...)` reactive-path style doesn't
 * apply here. This builds a real, started `AxonConfiguration` from this slice's own `configure`
 * function (`axon-testing-setup`'s "Integration Testing: Recording Published Events" pattern,
 * scoped to just this slice) instead, seeds prior events directly via `EventGateway`, and drives
 * [PaymentProviderAdapter.onWebhook] the way a real webhook controller would. Because
 * `PaymentProviderAdapter` dispatches with `commandGateway.sendAndWait`, every assertion below is
 * synchronous — no polling/await needed, unlike the automation's async reactive-path test.
 *
 * This test explicitly covers the two scenarios the task calls out by name: webhook redelivery
 * no-op (INV-RPR-2) and unknown-paymentId rejection (INV-RPR-3) — both exercised through the
 * adapter itself, not just the command handler fixture.
 */
class PaymentProviderAdapterIntegrationTest {

    private lateinit var configuration: AxonConfiguration
    private lateinit var adapter: PaymentProviderAdapter
    private lateinit var eventGateway: EventGateway

    // Collects every event published through the running EventStore/EventBus, via its own
    // subscribe(...) mechanism (EventStore extends EventBus/EventSink) — this works regardless
    // of how many decorators (e.g. InterceptingEventStore) wrap the component obtained from
    // configuration.getComponent(EventStore::class.java), unlike casting to a concrete recording
    // implementation.
    private val recorded = CopyOnWriteArrayList<Any>()

    private val paymentId = UUID.randomUUID()
    private val orderId = UUID.randomUUID()

    @BeforeEach
    fun beforeEach() {
        val configurer = RecordPaymentResultConfiguration().configure(EventSourcingConfigurer.create())
        configuration = configurer.start()
        eventGateway = configuration.getComponent(EventGateway::class.java)
        configuration.getComponent(EventStore::class.java).subscribe { events, _ ->
            events.forEach { recorded.add(it.payload()) }
            CompletableFuture.completedFuture(null)
        }
        adapter = PaymentProviderAdapter(configuration.getComponent(CommandGateway::class.java))
    }

    @AfterEach
    fun afterEach() {
        configuration.shutdown()
    }

    private fun seedPaymentRequested() {
        eventGateway.publish(listOf(PaymentRequested(orderId, paymentId, amountCents = 5000, requestedAt = Instant.parse("2026-08-20T10:05:00Z")))).join()
        recorded.clear()
    }

    private fun capturedEventsRecorded(): List<PaymentCaptured> = recorded.filterIsInstance<PaymentCaptured>()

    private fun webhookPayload(providerTransactionId: String = "ch_abc123") = PaymentProviderCapturePayload(
        paymentReference = paymentId.toString(),
        merchantOrderId = orderId.toString(),
        capturedAmount = "50.00",
        capturedAtIso = "2026-08-20T11:00:00Z",
        providerTransactionId = providerTransactionId,
    )

    // --- Happy path -----------------------------------------------------------------------

    @Test
    fun `happy path - given Payment Requested exists, when the webhook reports a capture, then Payment Captured is recorded`() {
        seedPaymentRequested()

        adapter.onWebhook(webhookPayload())

        val captured = capturedEventsRecorded()
        assertEquals(1, captured.size)
        assertEquals(paymentId, captured.single().paymentId)
        assertEquals("ch_abc123", captured.single().providerRef)
    }

    // --- Webhook redelivery no-op (INV-RPR-2), through the adapter ----------------------------

    @Test
    fun `webhook redelivery no-op (INV-RPR-2) - given the webhook already reported a capture, when the identical webhook is redelivered, then no second Payment Captured is recorded`() {
        seedPaymentRequested()

        adapter.onWebhook(webhookPayload())
        assertEquals(1, capturedEventsRecorded().size)

        adapter.onWebhook(webhookPayload()) // identical redelivery, same providerRef
        assertEquals(1, capturedEventsRecorded().size)
    }

    // --- Unknown paymentId rejection (INV-RPR-3), through the adapter -------------------------

    @Test
    fun `unknown paymentId rejection (INV-RPR-3) - given no Payment Requested exists, when the webhook arrives, then onWebhook throws and no Payment Captured is recorded`() {
        // Deliberately not seeding Payment Requested for this paymentId.
        val exception = assertFailsWith<Throwable> { adapter.onWebhook(webhookPayload()) }

        assertTrue(rootCauseChain(exception).any { it.message?.contains("INV-RPR-3") == true })
        assertTrue(capturedEventsRecorded().isEmpty())
    }

    // --- Malformed payload dropped at the boundary, through the adapter -----------------------

    @Test
    fun `malformed payload dropped at the boundary - given a payload with a non-UUID paymentReference, when the webhook arrives, then onWebhook does not throw and nothing is recorded`() {
        seedPaymentRequested()

        adapter.onWebhook(webhookPayload().copy(paymentReference = "not-a-uuid"))

        assertTrue(capturedEventsRecorded().isEmpty())
    }

    private fun rootCauseChain(throwable: Throwable): List<Throwable> =
        generateSequence(throwable) { it.cause }.toList()
}
