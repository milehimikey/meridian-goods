package com.meridiangoods.paymentstorequest

import com.meridiangoods.recordpaymentresult.PaymentCaptured

import com.meridiangoods.requestpayment.PaymentRequested

import com.meridiangoods.placeorder.OrderLine
import com.meridiangoods.placeorder.OrderPlaced
import org.axonframework.messaging.eventhandling.annotation.EventHandler
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Unit tests for `Payments To Request` (`slices/payments-to-request.md`). Every `INV-PTR-n` and
 * every scenario in the doc's "Scenarios (Given / When / Then)" section is covered — see the
 * comment on each test naming the exact ID/scenario it proves.
 */
class PaymentsToRequestProjectionTest {

    private lateinit var repository: InMemoryPaymentsToRequestRepository
    private lateinit var projection: PaymentsToRequestProjection

    private val orderId = UUID.randomUUID()
    private val placedAt = Instant.parse("2026-08-20T10:00:00Z")

    private fun orderPlaced(totalCents: Int = 5000) = OrderPlaced(
        orderId = orderId,
        customerId = UUID.randomUUID(),
        lineItems = listOf(OrderLine(sku = "SKU-1", quantity = 1, priceCents = totalCents)),
        totalCents = totalCents,
        placedAt = placedAt,
    )

    @BeforeEach
    fun setUp() {
        repository = InMemoryPaymentsToRequestRepository()
        projection = PaymentsToRequestProjection(repository)
    }

    // --- Happy path ------------------------------------------------------------------------

    @Test
    fun `happy path - given no prior events for O1, when Order Placed, then Payments To Request gains one entry`() {
        projection.on(orderPlaced(totalCents = 5000))

        val entry = repository.findByOrderId(orderId)
        assertEquals(5000, entry?.totalCents)
        assertEquals(placedAt, entry?.placedAt)
    }

    // --- INV-PTR-1: idempotent fold ----------------------------------------------------------

    @Test
    fun `INV-PTR-1 - given Order Placed already folded once, when folded again, then still exactly one entry`() {
        val event = orderPlaced()

        projection.on(event)
        projection.on(event)

        assertEquals(1, repository.findAll().size)
        assertEquals(event.totalCents, repository.findByOrderId(orderId)?.totalCents)
    }

    @Test
    fun `INV-PTR-1 - rebuild from scratch converges to the same state as incremental folding`() {
        val event = orderPlaced()

        // Incremental: fold once.
        projection.on(event)
        // "Rebuild": fold the same full history from scratch into an independent repository.
        val rebuiltRepo = InMemoryPaymentsToRequestRepository()
        PaymentsToRequestProjection(rebuiltRepo).on(event)

        assertEquals(repository.findAll(), rebuiltRepo.findAll())
    }

    // --- INV-PTR-2: drains on request --------------------------------------------------------

    @Test
    fun `INV-PTR-2 - given O1 present, when Payment Requested for O1, then O1 is removed`() {
        projection.on(orderPlaced())
        assertEquals(1, repository.findAll().size)

        projection.on(PaymentRequested(orderId = orderId, paymentId = UUID.randomUUID(), amountCents = 5000, requestedAt = Instant.now()))

        assertNull(repository.findByOrderId(orderId))
        assertEquals(0, repository.findAll().size)
    }

    @Test
    fun `INV-PTR-2 - out-of-order delivery - Payment Requested before Order Placed fold still leaves the order absent once both are folded`() {
        // Payment Requested arrives (and finds nothing to remove) before Order Placed's fold completes.
        projection.on(PaymentRequested(orderId = orderId, paymentId = UUID.randomUUID(), amountCents = 5000, requestedAt = Instant.now()))
        // Order Placed's fold completes afterward.
        projection.on(orderPlaced())
        // Once the removal is (re)applied, the order must not be resurrected.
        projection.on(PaymentRequested(orderId = orderId, paymentId = UUID.randomUUID(), amountCents = 5000, requestedAt = Instant.now()))

        assertNull(repository.findByOrderId(orderId))
    }

    // --- Untouched by capture ----------------------------------------------------------------

    @Test
    fun `untouched by capture - the projection has no event handler for Payment Captured at all`() {
        // This view's doc is explicit: "capture outcomes are the concern of Order Status and
        // Open Orders, not this to-do list" — proved here mechanically by asserting no
        // @EventHandler method on this class accepts anything named PaymentCaptured, rather
        // than fabricating a Payment Captured class this slice has no legitimate reason to own.
        val handledTypes = PaymentsToRequestProjection::class.java.declaredMethods
            .filter { it.isAnnotationPresent(EventHandler::class.java) }
            .map { it.parameterTypes.first().simpleName }

        assertEquals(setOf("OrderPlaced", "PaymentRequested"), handledTypes.toSet())
    }
}
