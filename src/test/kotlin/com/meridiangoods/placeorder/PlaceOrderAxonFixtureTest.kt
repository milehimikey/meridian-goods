package com.meridiangoods.placeorder

import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer
import org.axonframework.test.fixture.AxonTestFixture
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertTrue

/**
 * Tests for `Place Order` (`slices/place-order.md`). Every `INV-PO-n` and every scenario in the
 * doc's "Scenarios (Given / When / Then)" section is covered — see the comment on each test
 * naming the exact ID/scenario it proves.
 */
class PlaceOrderAxonFixtureTest {

    private lateinit var fixture: AxonTestFixture

    private val orderId = UUID.randomUUID()
    private val customerId = UUID.randomUUID()

    @BeforeEach
    fun beforeEach() {
        val configurer = PlaceOrderConfiguration().configure(EventSourcingConfigurer.create())
        fixture = AxonTestFixture.with(configurer)
    }

    @AfterEach
    fun afterEach() {
        fixture.stop()
    }

    // --- Happy path -----------------------------------------------------------------------

    @Test
    fun `happy path - given a fresh orderId with a correct total, when Place Order, then Order Placed is recorded`() {
        val lineItems = listOf(
            OrderLine(sku = "SKU-1", quantity = 2, priceCents = 1000),
            OrderLine(sku = "SKU-2", quantity = 1, priceCents = 2200),
        )

        fixture.given()
            .`when`()
            .command(PlaceOrder(orderId, customerId, lineItems, totalCents = 4200))
            .then()
            .success()
            .eventsSatisfy { events ->
                assertTrue(events.size == 1)
                val event = events.single().payload() as OrderPlaced
                assertTrue(event.orderId == orderId)
                assertTrue(event.customerId == customerId)
                assertTrue(event.lineItems == lineItems)
                assertTrue(event.totalCents == 4200)
            }
    }

    // --- INV-PO-2: total mismatch -----------------------------------------------------------

    @Test
    fun `INV-PO-2 - given a cart whose true sum is 4200, when Place Order with totalCents 4000, then rejected as total mismatch`() {
        val lineItems = listOf(
            OrderLine(sku = "SKU-1", quantity = 2, priceCents = 1000),
            OrderLine(sku = "SKU-2", quantity = 1, priceCents = 2200),
        )

        fixture.given()
            .`when`()
            .command(PlaceOrder(orderId, customerId, lineItems, totalCents = 4000))
            .then()
            .exceptionSatisfies { exception ->
                assertTrue(exception.message?.contains("INV-PO-2") == true)
            }
    }

    // --- INV-PO-3: empty order ---------------------------------------------------------------

    @Test
    fun `INV-PO-3 - given a cart with zero line items, when Place Order, then rejected as empty order`() {
        fixture.given()
            .`when`()
            .command(PlaceOrder(orderId, customerId, emptyList(), totalCents = 0))
            .then()
            .exceptionSatisfies { exception ->
                assertTrue(exception.message?.contains("INV-PO-3") == true)
            }
    }

    // --- INV-PO-4: invalid quantity -----------------------------------------------------------

    @Test
    fun `INV-PO-4 - given a line item with quantity -1, when Place Order, then rejected citing the offending line item`() {
        val lineItems = listOf(OrderLine(sku = "SKU-1", quantity = -1, priceCents = 1000))

        fixture.given()
            .`when`()
            .command(PlaceOrder(orderId, customerId, lineItems, totalCents = -1000))
            .then()
            .exceptionSatisfies { exception ->
                assertTrue(exception.message?.contains("INV-PO-4") == true)
                assertTrue(exception.message?.contains("SKU-1") == true)
            }
    }

    @Test
    fun `INV-PO-4 - given a line item with quantity 0, when Place Order, then rejected citing the offending line item`() {
        val lineItems = listOf(OrderLine(sku = "SKU-1", quantity = 0, priceCents = 1000))

        fixture.given()
            .`when`()
            .command(PlaceOrder(orderId, customerId, lineItems, totalCents = 0))
            .then()
            .exceptionSatisfies { exception ->
                assertTrue(exception.message?.contains("INV-PO-4") == true)
                assertTrue(exception.message?.contains("SKU-1") == true)
            }
    }

    // --- INV-PO-1: idempotent retry (identical payload) -----------------------------------------

    @Test
    fun `INV-PO-1 - duplicate-orderId retry - given Order Placed already recorded, when retried with the identical payload, then no second event is recorded`() {
        val lineItems = listOf(OrderLine(sku = "SKU-1", quantity = 2, priceCents = 1000))
        val original = OrderPlaced(orderId, customerId, lineItems, totalCents = 2000, placedAt = java.time.Instant.now())

        fixture.given()
            .event(original)
            .`when`()
            .command(PlaceOrder(orderId, customerId, lineItems, totalCents = 2000))
            .then()
            .success()
            .noEvents()
    }

    // --- INV-PO-1: conflicting duplicate orderId -------------------------------------------------

    @Test
    fun `INV-PO-1 - conflicting duplicate orderId - given Order Placed already recorded, when a different payload reuses orderId, then rejected as a conflict`() {
        val originalLineItems = listOf(OrderLine(sku = "SKU-1", quantity = 2, priceCents = 1000))
        val original = OrderPlaced(orderId, customerId, originalLineItems, totalCents = 2000, placedAt = java.time.Instant.now())

        val conflictingLineItems = listOf(OrderLine(sku = "SKU-2", quantity = 1, priceCents = 5000))

        fixture.given()
            .event(original)
            .`when`()
            .command(PlaceOrder(orderId, customerId, conflictingLineItems, totalCents = 5000))
            .then()
            .exceptionSatisfies { exception ->
                assertTrue(exception.message?.contains("INV-PO-1") == true)
            }
    }
}
