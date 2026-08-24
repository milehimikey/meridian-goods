package com.meridiangoods.requestpayment

import com.meridiangoods.paymentstorequest.InMemoryPaymentsToRequestRepository
import com.meridiangoods.paymentstorequest.PaymentsToRequestRepository
import com.meridiangoods.placeorder.OrderPlaced
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer
import org.axonframework.test.fixture.AxonTestFixture
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertTrue

/**
 * Synchronous, `given().when().command().then()` tests for `RequestPaymentCommandHandler`'s
 * decision logic (`slices/request-payment.md`). This is the fast, deterministic half of this
 * slice's tests — see [WhenOrderPlacedThenRequestPaymentFromQueueReactiveTest] for the slower
 * proof that `Order Placed` actually triggers the automation end to end (per
 * `axon-policy-testing`'s "Unit-Testing the Command-Handler Half" guidance).
 *
 * The automation registered by [RequestPaymentConfiguration] now depends on a
 * `PaymentsToRequestRepository` component (see [WhenOrderPlacedThenRequestPaymentFromQueue]) —
 * a bare in-memory instance is registered here purely so the slice's configurer starts; none of
 * these tests exercise the automation's reactive path (that's covered by the reactive test
 * above).
 */
class RequestPaymentCommandHandlerTest {

    private lateinit var fixture: AxonTestFixture

    private val orderId = UUID.randomUUID()
    private val paymentId = UUID.randomUUID()
    private val requestedAt: Instant = Instant.parse("2026-08-20T10:05:00Z")

    @BeforeEach
    fun beforeEach() {
        val configurer = EventSourcingConfigurer.create()
            .componentRegistry { cr ->
                cr.registerComponent(PaymentsToRequestRepository::class.java) { InMemoryPaymentsToRequestRepository() }
            }
            .let(RequestPaymentConfiguration()::configure)
        fixture = AxonTestFixture.with(configurer)
    }

    @AfterEach
    fun afterEach() {
        fixture.stop()
    }

    // --- Happy path -----------------------------------------------------------------------

    @Test
    fun `happy path - given an order placed for 5000, when Request Payment for 5000, then Payment Requested is recorded`() {
        val orderPlaced = orderPlacedEvent(totalCents = 5000)

        fixture.given()
            .event(orderPlaced)
            .`when`()
            .command(RequestPayment(orderId, paymentId, amountCents = 5000, requestedAt = requestedAt))
            .then()
            .success()
            .eventsSatisfy { events ->
                assertTrue(events.size == 1)
                val event = events.single().payload() as PaymentRequested
                assertTrue(event.orderId == orderId)
                assertTrue(event.paymentId == paymentId)
                assertTrue(event.amountCents == 5000)
                assertTrue(event.requestedAt == requestedAt)
            }
    }

    // --- INV-RP-1: exactly-once liveness -----------------------------------------------------

    @Test
    fun `INV-RP-1 - given Payment Requested already recorded for this order, when Request Payment is issued again (re-poll), then no second event is recorded`() {
        val orderPlaced = orderPlacedEvent(totalCents = 5000)
        val alreadyRequested = PaymentRequested(orderId, paymentId, amountCents = 5000, requestedAt = requestedAt)

        // A fresh paymentId simulates the doc's "processor's next poll" scenario: even a
        // different minted paymentId cannot produce a second event once the order is already
        // requested — INV-RP-1 is keyed on orderId, not paymentId.
        val secondAttemptPaymentId = UUID.randomUUID()

        fixture.given()
            .event(orderPlaced)
            .event(alreadyRequested)
            .`when`()
            .command(RequestPayment(orderId, secondAttemptPaymentId, amountCents = 5000, requestedAt = requestedAt.plusSeconds(60)))
            .then()
            .success()
            .noEvents()
    }

    // --- INV-RP-2: amount fidelity -----------------------------------------------------------

    @Test
    fun `INV-RP-2 - given an order placed for 5000, when Request Payment for 4000, then rejected as amount mismatch`() {
        val orderPlaced = orderPlacedEvent(totalCents = 5000)

        fixture.given()
            .event(orderPlaced)
            .`when`()
            .command(RequestPayment(orderId, paymentId, amountCents = 4000, requestedAt = requestedAt))
            .then()
            .exceptionSatisfies { exception ->
                assertTrue(exception.message?.contains("INV-RP-2") == true)
            }
    }

    @Test
    fun `INV-RP-2 - given no order was ever placed, when Request Payment, then rejected as amount mismatch (nothing to be faithful to)`() {
        fixture.given()
            .`when`()
            .command(RequestPayment(orderId, paymentId, amountCents = 5000, requestedAt = requestedAt))
            .then()
            .exceptionSatisfies { exception ->
                assertTrue(exception.message?.contains("INV-RP-2") == true)
            }
    }

    // --- INV-RP-3: no back-door writes ---------------------------------------------------------

    @Test
    fun `INV-RP-3 - the automation's react method has no EventAppender parameter, so it cannot append Payment Requested directly`() {
        // Structural proof that the only path to Payment Requested is through this command
        // handler: WhenOrderPlacedThenRequestPaymentFromQueue.react holds a CommandDispatcher,
        // never an EventAppender, and RequestPaymentCommandHandler.handle (exercised by the
        // happy-path test above) is the only method in this slice that does.
        val reactMethod = WhenOrderPlacedThenRequestPaymentFromQueue::class.java.declaredMethods
            .single { it.name == "react" }
        assertTrue(reactMethod.parameterTypes.none { it == org.axonframework.messaging.eventhandling.gateway.EventAppender::class.java })

        val handleMethod = RequestPaymentCommandHandler::class.java.declaredMethods
            .single { it.name == "handle" }
        assertTrue(handleMethod.parameterTypes.any { it == org.axonframework.messaging.eventhandling.gateway.EventAppender::class.java })
    }

    private fun orderPlacedEvent(totalCents: Int): OrderPlaced = OrderPlaced(
        orderId = orderId,
        customerId = UUID.randomUUID(),
        lineItems = emptyList(),
        totalCents = totalCents,
        placedAt = Instant.parse("2026-08-20T10:00:00Z"),
    )
}
