package com.meridiangoods.cancelorder

import com.meridiangoods.placeorder.OrderLine
import com.meridiangoods.placeorder.OrderPlaced
import com.meridiangoods.recordpaymentresult.PaymentCaptured
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer
import org.axonframework.test.fixture.AxonTestFixture
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertTrue

/**
 * Tests for `Cancel Order` (`slices/cancel-order.md`). Every `INV-CO-n` and every scenario in
 * the doc's "Scenarios (Given / When / Then)" section is covered — see the comment on each test
 * naming the exact ID/scenario it proves. The fixture is built from [CancelOrderConfiguration]
 * alone (per `axon-entity-testing`); `.given().event(...)` injects `Order Placed` /
 * `Payment Captured` directly regardless of which slice originally produced them, which is all
 * this slice's own DCB criteria needs to see them (see [CancelOrderCommandHandler.State]'s doc
 * comment for the criteria design).
 */
class CancelOrderAxonFixtureTest {

    private lateinit var fixture: AxonTestFixture

    private val orderId = UUID.randomUUID()
    private val customerId = UUID.randomUUID()
    private val otherCustomerId = UUID.randomUUID()

    @BeforeEach
    fun beforeEach() {
        val configurer = CancelOrderConfiguration().configure(EventSourcingConfigurer.create())
        fixture = AxonTestFixture.with(configurer)
    }

    @AfterEach
    fun afterEach() {
        fixture.stop()
    }

    private fun orderPlaced(customer: UUID = customerId) = OrderPlaced(
        orderId = orderId,
        customerId = customer,
        lineItems = listOf(OrderLine(sku = "SKU-1", quantity = 1, priceCents = 5000)),
        totalCents = 5000,
        placedAt = Instant.parse("2026-08-20T10:00:00Z"),
    )

    private fun paymentCaptured() = PaymentCaptured(
        paymentId = UUID.randomUUID(),
        orderId = orderId,
        amountCents = 5000,
        capturedAt = Instant.parse("2026-08-20T11:00:00Z"),
        providerRef = "PROV-REF-1",
    )

    // --- Happy path ------------------------------------------------------------------------

    @Test
    fun `happy path - given Order Placed and no Payment Captured, when Cancel Order, then Order Cancelled is recorded`() {
        fixture.given()
            .event(orderPlaced())
            .`when`()
            .command(CancelOrder(orderId, customerId))
            .then()
            .success()
            .eventsSatisfy { events ->
                assertTrue(events.size == 1)
                val event = events.single().payload() as OrderCancelled
                assertTrue(event.orderId == orderId)
                assertTrue(event.customerId == customerId)
            }
    }

    // --- INV-CO-1: reject after capture ------------------------------------------------------

    @Test
    fun `INV-CO-1 - given Order Placed and Payment Captured, when Cancel Order, then rejected as too-late-to-cancel`() {
        fixture.given()
            .event(orderPlaced())
            .event(paymentCaptured())
            .`when`()
            .command(CancelOrder(orderId, customerId))
            .then()
            .exceptionSatisfies { exception ->
                assertTrue(exception.message?.contains("INV-CO-1") == true)
            }
    }

    // --- INV-CO-2: idempotent retry -----------------------------------------------------------

    @Test
    fun `INV-CO-2 - given Order Cancelled already recorded, when Cancel Order again, then no second event is recorded`() {
        fixture.given()
            .event(orderPlaced())
            .event(OrderCancelled(orderId, customerId, Instant.parse("2026-08-20T12:00:00Z")))
            .`when`()
            .command(CancelOrder(orderId, customerId))
            .then()
            .success()
            .noEvents()
    }

    // --- Rejected: wrong customer ---------------------------------------------------------------

    @Test
    fun `Rejected - wrong customer - given Order Placed for C1, when C2 submits Cancel Order, then rejected as unauthorized`() {
        fixture.given()
            .event(orderPlaced(customer = customerId))
            .`when`()
            .command(CancelOrder(orderId, otherCustomerId))
            .then()
            .exceptionSatisfies { exception ->
                assertTrue(exception.message?.contains("unauthorized") == true)
            }
    }

    // --- Rejected: unknown order -----------------------------------------------------------------

    @Test
    fun `Rejected - unknown order - given no Order Placed event, when Cancel Order, then rejected as unknown`() {
        fixture.given()
            .`when`()
            .command(CancelOrder(orderId, customerId))
            .then()
            .exceptionSatisfies { exception ->
                assertTrue(exception.message?.contains("unknown order") == true)
            }
    }

    // --- Race with capture: cancel decides first, before capture lands ------------------------

    @Test
    fun `Race with capture - given Order Placed only, when Cancel Order decides before Payment Captured lands, then it succeeds`() {
        // Documents the doc's "Alternate & Error Flows" race note: whichever fact this command
        // handler's own consistency boundary has actually recorded at decision time governs —
        // if Payment Captured hasn't landed yet, cancellation proceeds normally.
        fixture.given()
            .event(orderPlaced())
            .`when`()
            .command(CancelOrder(orderId, customerId))
            .then()
            .success()
            .eventsSatisfy { events -> assertTrue(events.size == 1) }
    }
}
