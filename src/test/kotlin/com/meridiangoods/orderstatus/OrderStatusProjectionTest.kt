package com.meridiangoods.orderstatus

import com.meridiangoods.placeorder.OrderLine
import com.meridiangoods.placeorder.OrderPlaced
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Unit tests for `Order Status` (`slices/order-status.md`). Every `INV-OS-n` and every scenario
 * in the doc's "Scenarios (Given / When / Then)" section is covered — see the comment on each
 * test naming the exact ID/scenario it proves.
 */
class OrderStatusProjectionTest {

    private lateinit var repository: InMemoryOrderStatusRepository
    private lateinit var projection: OrderStatusProjection

    private val orderId = UUID.randomUUID()
    private val placedAt = Instant.parse("2026-08-20T10:00:00Z")
    private val requestedAt = Instant.parse("2026-08-20T10:05:00Z")
    private val capturedAt = Instant.parse("2026-08-20T10:10:00Z")

    private fun orderPlaced() = OrderPlaced(
        orderId = orderId,
        customerId = UUID.randomUUID(),
        lineItems = listOf(OrderLine(sku = "SKU-1", quantity = 1, priceCents = 5000)),
        totalCents = 5000,
        placedAt = placedAt,
    )

    private fun paymentRequested() =
        PaymentRequested(UUID.randomUUID(), orderId, 5000, requestedAt)

    private fun paymentCaptured() =
        PaymentCaptured(UUID.randomUUID(), orderId, 5000, capturedAt, "PROV-REF-1")

    @BeforeEach
    fun setUp() {
        repository = InMemoryOrderStatusRepository()
        projection = OrderStatusProjection(repository)
    }

    // --- `Order Placed` lands (INV-OS-2: reads as "placed") ----------------------------------

    @Test
    fun `Order Placed lands - given no prior row, when Order Placed, then a new row appears reading as placed`() {
        projection.on(orderPlaced())

        val row = repository.findByOrderId(orderId)
        assertEquals(placedAt, row?.placedAt)
        assertNull(row?.requestedAt)
        assertNull(row?.capturedAt)
        assertEquals(OrderStatus.PLACED, row?.status) // INV-OS-2
    }

    // --- `Payment Requested` lands (INV-OS-2: reads as "payment requested") -------------------

    @Test
    fun `Payment Requested lands - given a placed row, when Payment Requested, then the row updates in place reading as payment requested`() {
        projection.on(orderPlaced())

        projection.on(paymentRequested())

        assertEquals(1, repository.findAll().size)
        val row = repository.findByOrderId(orderId)
        assertEquals(requestedAt, row?.requestedAt)
        assertEquals(OrderStatus.PAYMENT_REQUESTED, row?.status) // INV-OS-2
    }

    // --- `Payment Captured` lands (INV-OS-2: reads as "paid") ---------------------------------

    @Test
    fun `Payment Captured lands - given a requested row, when Payment Captured, then the row updates in place reading as paid`() {
        projection.on(orderPlaced())
        projection.on(paymentRequested())

        projection.on(paymentCaptured())

        assertEquals(1, repository.findAll().size)
        val row = repository.findByOrderId(orderId)
        assertEquals(capturedAt, row?.capturedAt)
        assertEquals(OrderStatus.PAID, row?.status) // INV-OS-2
    }

    // --- INV-OS-1: idempotent fold / redelivery ------------------------------------------------

    @Test
    fun `INV-OS-1 - redelivered Order Placed leaves the row unchanged and status unchanged`() {
        val event = orderPlaced()
        projection.on(event)
        projection.on(event)

        assertEquals(1, repository.findAll().size)
        assertEquals(OrderStatus.PLACED, repository.findByOrderId(orderId)?.status)
    }

    @Test
    fun `INV-OS-1 - redelivered Payment Requested leaves the row unchanged and status unchanged`() {
        projection.on(orderPlaced())
        val event = paymentRequested()
        projection.on(event)
        projection.on(event)

        assertEquals(1, repository.findAll().size)
        assertEquals(OrderStatus.PAYMENT_REQUESTED, repository.findByOrderId(orderId)?.status)
    }

    @Test
    fun `INV-OS-1 - redelivered Payment Captured leaves the row unchanged and status unchanged`() {
        projection.on(orderPlaced())
        projection.on(paymentRequested())
        val event = paymentCaptured()
        projection.on(event)
        projection.on(event)

        assertEquals(1, repository.findAll().size)
        assertEquals(OrderStatus.PAID, repository.findByOrderId(orderId)?.status)
    }

    // --- Alternate flow: no payment yet ---------------------------------------------------------

    @Test
    fun `no payment yet - an order can sit indefinitely at placed with requestedAt and capturedAt absent`() {
        projection.on(orderPlaced())

        val row = repository.findByOrderId(orderId)
        assertNull(row?.requestedAt)
        assertNull(row?.capturedAt)
        assertEquals(OrderStatus.PLACED, row?.status)
    }
}
