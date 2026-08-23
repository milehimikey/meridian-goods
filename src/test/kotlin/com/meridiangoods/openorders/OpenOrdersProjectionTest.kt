package com.meridiangoods.openorders

import com.meridiangoods.recordpaymentresult.PaymentCaptured

import com.meridiangoods.cancelorder.OrderCancelled
import com.meridiangoods.placeorder.OrderLine
import com.meridiangoods.placeorder.OrderPlaced
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull

/**
 * Unit tests for `Open Orders` (`slices/open-orders.md`). Every `INV-OO-n` and every scenario in
 * the doc's "Scenarios (Given / When / Then)" section is covered — see the comment on each test
 * naming the exact ID/scenario it proves.
 *
 * Also covers `Open Orders — cancelled` (`slices/open-orders-cancelled.md`, INV-OOC-1/
 * INV-OOC-2) in the section below, kept in this same class rather than a separate one: it is the
 * repeated-view fold on this exact projection/repository, not a distinct slice's own class — see
 * [OpenOrdersProjection]'s doc comment for the placement rationale.
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

    private val cancelledAt = Instant.parse("2026-08-20T12:00:00Z")

    private fun orderCancelled() = OrderCancelled(
        orderId = orderId,
        customerId = customerId,
        cancelledAt = cancelledAt,
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

    // === `Open Orders — cancelled` (`slices/open-orders-cancelled.md`) =========================
    // The repeated `Open Orders` instance's removal fold, on the same read model/repository.

    // --- `Order Cancelled` lands for an open order --------------------------------------------

    @Test
    fun `Order Cancelled lands for an open order - given an Open Orders row, when Order Cancelled, then the row is removed`() {
        projection.on(orderPlaced())
        assertNotNull(repository.findByOrderId(orderId))

        projection.on(orderCancelled())

        assertNull(repository.findByOrderId(orderId))
        assertEquals(0, repository.findAll().size)
    }

    // --- INV-OOC-2: redelivered event -----------------------------------------------------------

    @Test
    fun `INV-OOC-2 - redelivered Order Cancelled after removal is a no-op, no error, no second effect`() {
        projection.on(orderPlaced())
        projection.on(orderCancelled())
        assertNull(repository.findByOrderId(orderId))

        projection.on(orderCancelled())

        assertNull(repository.findByOrderId(orderId))
        assertEquals(0, repository.findAll().size)
    }

    // --- Cancellation before capture reaches the board ------------------------------------------

    @Test
    fun `Cancellation before capture reaches the board - an open order with no capturedAt is removed the same way`() {
        projection.on(orderPlaced())
        assertNull(repository.findByOrderId(orderId)?.capturedAt)

        projection.on(orderCancelled())

        assertNull(repository.findByOrderId(orderId))
    }

    // --- Alternate flow: out-of-order delivery (Order Cancelled arrives before Order Placed) ----

    @Test
    fun `out-of-order delivery - Order Cancelled before Order Placed suppresses row creation`() {
        projection.on(orderCancelled())
        assertNull(repository.findByOrderId(orderId))

        projection.on(orderPlaced())

        assertNull(repository.findByOrderId(orderId))
        assertEquals(0, repository.findAll().size)
    }
}
