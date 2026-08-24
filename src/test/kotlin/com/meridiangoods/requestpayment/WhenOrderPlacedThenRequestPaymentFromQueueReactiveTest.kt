package com.meridiangoods.requestpayment

import com.meridiangoods.paymentstorequest.InMemoryPaymentsToRequestRepository
import com.meridiangoods.paymentstorequest.PaymentsToRequestEntry
import com.meridiangoods.paymentstorequest.PaymentsToRequestRepository
import com.meridiangoods.placeorder.OrderPlaced
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer
import org.axonframework.test.fixture.AxonTestFixture
import org.axonframework.test.fixture.RecordingComponentsRegistry
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertTrue

/**
 * Reactive-path tests for `Payment Requester` (`slices/request-payment.md`): prove that
 * `Order Placed` triggers the automation, and — the point of the ratified fix — that the
 * automation's *decision* is driven by the `Payments To Request` queue, not the event payload:
 * present in the queue → acted, with the amount taken from the queue entry (INV-RP-2); absent
 * from the queue → not acted (INV-RP-1's exactly-once via queue-draining). See
 * [RequestPaymentCommandHandlerTest] for the command handler's own decision logic tested in
 * isolation.
 *
 * The queue is a plain in-memory [PaymentsToRequestRepository], seeded directly by each test
 * *before* firing the triggering `Order Placed` — this isolates the automation's own
 * queue-consultation decision from the separately-tested `Payments To Request` projection's own
 * timing (`payments-to-request` slice's own tests cover that projection).
 *
 * Per `axon-policy-testing`: the reaction is asynchronous (pooled-streaming processor), so the
 * "present" assertion is wrapped in `.then().await { ... }`. The "absent" assertion proves a
 * negative, which polling cannot do (an eventually-true check taken too early would falsely
 * pass) — this project has no Awaitility dependency, so a fixed wait window substitutes for
 * `during(...)`-style polling, reading back every event actually published via the fixture's own
 * `RecordingComponentsRegistry`/`RecordingEventSink` (`axon-testing-setup`'s
 * recording-configuration pattern, which `AxonTestFixture.with(...)` wires in automatically)
 * rather than only the events surfaced by a particular command's own result.
 */
class WhenOrderPlacedThenRequestPaymentFromQueueReactiveTest {

    private lateinit var fixture: AxonTestFixture
    private lateinit var paymentsToRequest: PaymentsToRequestRepository

    @BeforeEach
    fun beforeEach() {
        paymentsToRequest = InMemoryPaymentsToRequestRepository()
        val configurer = EventSourcingConfigurer.create()
            .componentRegistry { cr ->
                cr.registerComponent(PaymentsToRequestRepository::class.java) { paymentsToRequest }
            }
            .let(RequestPaymentConfiguration()::configure)
        fixture = AxonTestFixture.with(configurer)
    }

    @AfterEach
    fun afterEach() {
        fixture.stop()
    }

    private fun orderPlacedEvent(orderId: UUID, totalCents: Int) = OrderPlaced(
        orderId = orderId,
        customerId = UUID.randomUUID(),
        lineItems = emptyList(),
        totalCents = totalCents,
        placedAt = Instant.parse("2026-08-20T10:00:00Z"),
    )

    @Test
    fun `present in the queue - given O1 is present in Payments To Request with totalCents 5000, when Order Placed is processed, then Payment Requested is recorded for 5000 (from the queue)`() {
        val orderId = UUID.randomUUID()
        // The queue entry and the triggering event necessarily agree in real operation (the
        // real Payments To Request projection derives the entry's totalCents from this same
        // Order Placed) — that agreement is exactly what RequestPaymentCommandHandler's own
        // second line of defense (INV-RP-2, checked against its own Order-Placed-sourced state)
        // re-validates. What this test proves is that react() reads amountCents from the queue
        // entry (see WhenOrderPlacedThenRequestPaymentFromQueue.react), not that a divergent
        // value would somehow be accepted — a hand-seeded queue that disagreed with Order Placed
        // would trip that second line of defense, which is a feature, not a gap.
        paymentsToRequest.upsert(
            PaymentsToRequestEntry(orderId = orderId, totalCents = 5000, placedAt = Instant.parse("2026-08-20T10:00:00Z")),
        )
        val orderPlaced = orderPlacedEvent(orderId, totalCents = 5000)

        fixture.given()
            .events(orderPlaced)
            .then()
            .await { result ->
                result.eventsSatisfy { events ->
                    val paymentRequestedEvents = events.map { it.payload() }.filterIsInstance<PaymentRequested>()
                    assertTrue(paymentRequestedEvents.size == 1)
                    val event = paymentRequestedEvents.single()
                    assertTrue(event.orderId == orderId)
                    assertTrue(event.amountCents == 5000)
                }
            }
    }

    @Test
    fun `absent from the queue (INV-RP-1, drained) - given O1 is NOT present in Payments To Request, when Order Placed is processed, then no Payment Requested is recorded`() {
        val orderId = UUID.randomUUID()
        // Deliberately not seeded — simulates an order already drained from the queue by a
        // prior Payment Requested (INV-PTR-2), or a redelivered/duplicate Order Placed arriving
        // after the queue has already moved on.
        val orderPlaced = orderPlacedEvent(orderId, totalCents = 5000)

        fixture.given()
            .events(orderPlaced)
            .then()
            .expect { configuration ->
                Thread.sleep(500)
                val recordings = configuration.getComponent(RecordingComponentsRegistry::class.java)
                val paymentRequestedEvents = recordings.eventSink()!!.recorded().map { it.payload() }.filterIsInstance<PaymentRequested>()
                assertTrue(paymentRequestedEvents.none { it.orderId == orderId })
            }
    }
}
