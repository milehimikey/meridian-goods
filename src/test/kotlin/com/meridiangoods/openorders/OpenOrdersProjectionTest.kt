package com.meridiangoods.openorders

import com.meridiangoods.recordpaymentresult.PaymentCaptured

import com.meridiangoods.placeorder.OrderLine
import com.meridiangoods.placeorder.OrderPlaced
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Unit tests for `Open Orders` (`slices/open-orders.md`). Every `INV-OO-n` and every scenario in
 * the doc's "Scenarios (Given / When / Then)" section is covered — see the comment on each test
 * naming the exact ID/scenario it proves.
 */
class OpenOrdersProjectionTest {

    private lateinit var repository: InMemoryOpenOrdersRepository
    private lateinit var projection: OpenOrdersProjection

    private val orderId = UUID.randomUUID()
    private val customerId = UUID.randomUUID()
    private val placedAt = Instant.parse("2026-08-20T10:00:00Z")
    private val capturedAt = Instant.parse("2026-08-20T11:00:00Z")

    private fun orderPlaced() = OrderPlaced(
        orderId = orderId,
        customerId = customerId,
        lineItems = listOf(OrderLine(sku = "SKU-1", quantity = 1, priceCents = 5000)),
        totalCents = 5000,
        placedAt = placedAt,
    )

    private fun paymentCaptured() = PaymentCaptured(
        paymentId = UUID.randomUUID(),
        orderId = orderId,
        amountCents = 5000,
        capturedAt = capturedAt,
        providerRef = "PROV-REF-1",
    )

    @BeforeEach
    fun setUp() {
        repository = InMemoryOpenOrdersRepository()
        projection = OpenOrdersProjection(repository)
    }

    // --- `Order Placed` lands ---------------------------------------------------------------

    @Test
    fun `Order Placed lands - given no prior row, when Order Placed, then a new row appears with capturedAt absent`() {
        projection.on(orderPlaced())

        val row = repository.findByOrderId(orderId)
        assertEquals(customerId, row?.customerId)
        assertEquals(5000, row?.totalCents)
        assertEquals(placedAt, row?.placedAt)
        assertNull(row?.capturedAt)
    }

    // --- `Payment Captured` lands ------------------------------------------------------------

    @Test
    fun `Payment Captured lands - given an existing row, when Payment Captured, then that row is updated in place`() {
        projection.on(orderPlaced())

        projection.on(paymentCaptured())

        assertEquals(1, repository.findAll().size)
        val row = repository.findByOrderId(orderId)
        assertEquals(capturedAt, row?.capturedAt)
        assertEquals(customerId, row?.customerId)
    }

    // --- INV-OO-1: idempotent fold / redelivery -----------------------------------------------

    @Test
    fun `INV-OO-1 - redelivered Order Placed leaves the row unchanged, no duplicate`() {
        val event = orderPlaced()
        projection.on(event)
        projection.on(event)

        assertEquals(1, repository.findAll().size)
        assertEquals(placedAt, repository.findByOrderId(orderId)?.placedAt)
    }

    @Test
    fun `INV-OO-1 - redelivered Payment Captured leaves the row unchanged, no duplicate`() {
        projection.on(orderPlaced())
        val event = paymentCaptured()
        projection.on(event)
        projection.on(event)

        assertEquals(1, repository.findAll().size)
        assertEquals(capturedAt, repository.findByOrderId(orderId)?.capturedAt)
    }

    // --- INV-OO-2: capturedAt only from a real Payment Captured -------------------------------

    @Test
    fun `INV-OO-2 - capturedAt stays absent until Payment Captured actually lands`() {
        projection.on(orderPlaced())

        assertNull(repository.findByOrderId(orderId)?.capturedAt)
    }

    // --- Alternate flow: out-of-order delivery -------------------------------------------------

    @Test
    fun `out-of-order delivery - Payment Captured before Order Placed still converges to a complete row`() {
        projection.on(paymentCaptured())
        val partial = repository.findByOrderId(orderId)
        assertEquals(capturedAt, partial?.capturedAt)
        assertNull(partial?.placedAt)

        projection.on(orderPlaced())

        val row = repository.findByOrderId(orderId)
        assertEquals(1, repository.findAll().size)
        assertEquals(capturedAt, row?.capturedAt)
        assertEquals(placedAt, row?.placedAt)
        assertEquals(customerId, row?.customerId)
    }
}
