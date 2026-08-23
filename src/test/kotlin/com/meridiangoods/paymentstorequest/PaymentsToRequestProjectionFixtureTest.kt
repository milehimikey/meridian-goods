package com.meridiangoods.paymentstorequest

import com.meridiangoods.requestpayment.PaymentRequested

import com.meridiangoods.placeorder.OrderLine
import com.meridiangoods.placeorder.PlaceOrder
import com.meridiangoods.placeorder.PlaceOrderConfiguration
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer
import org.axonframework.test.fixture.AxonTestFixture
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Fixture-level test proving [PaymentsToRequestConfiguration]'s wiring — the slice really does
 * register the projection against a processor and the query handler against the shared
 * repository component, driven by a real command from the already-merged `Place Order` slice.
 * A couple of representative cases, not a re-run of every [PaymentsToRequestProjectionTest]
 * scenario (see `axon-projection-testing`).
 */
class PaymentsToRequestProjectionFixtureTest {

    private lateinit var repository: InMemoryPaymentsToRequestRepository
    private lateinit var fixture: AxonTestFixture

    private val orderId = UUID.randomUUID()

    @BeforeEach
    fun beforeEach() {
        repository = InMemoryPaymentsToRequestRepository()
        val configurer = PaymentsToRequestConfiguration()
            .configure(PlaceOrderConfiguration().configure(EventSourcingConfigurer.create()))
            .componentRegistry { cr -> cr.registerComponent(PaymentsToRequestRepository::class.java) { repository } }
        fixture = AxonTestFixture.with(configurer)
    }

    @AfterEach
    fun afterEach() = fixture.stop()

    @Test
    fun `given a real Place Order command, when Order Placed is recorded, then the queue gains an entry via real wiring`() {
        val lineItems = listOf(OrderLine(sku = "SKU-1", quantity = 2, priceCents = 1000))

        fixture.given()
            .`when`()
            .command(PlaceOrder(orderId, UUID.randomUUID(), lineItems, totalCents = 2000))
            .then()
            .success()

        awaitUntil { repository.findByOrderId(orderId) != null }
        assertEquals(2000, repository.findByOrderId(orderId)?.totalCents)
    }

    @Test
    fun `given a queued order, when Payment Requested is folded directly, then the queue drains via real wiring`() {
        val lineItems = listOf(OrderLine(sku = "SKU-1", quantity = 1, priceCents = 1000))

        fixture.given()
            .`when`()
            .command(PlaceOrder(orderId, UUID.randomUUID(), lineItems, totalCents = 1000))
            .then()
            .success()

        awaitUntil { repository.findByOrderId(orderId) != null }
        assertEquals(1000, repository.findByOrderId(orderId)?.totalCents)

        fixture.given()
            .event(PaymentRequested(orderId = orderId, paymentId = UUID.randomUUID(), amountCents = 1000, requestedAt = java.time.Instant.now()))
            .`when`()
            .nothing()
            .then()
            .noEvents()

        awaitUntil { repository.findByOrderId(orderId) == null }
        assertNull(repository.findByOrderId(orderId))
    }

    /**
     * The fixture's `then()` only awaits the write-side publish completing (the underlying
     * `AxonTestWhen.awaitCompletion`), not the pooled-streaming processor draining its segments
     * (that runs on its own background `Coordinator`, per real Axon 5.3.1 — confirmed by
     * decompiling `axon-test-5.3.1.jar`). A wiring-level test against a `pooledStreaming`
     * processor therefore needs a short poll even at fixture level; see `w3-2-views.md` for the
     * full note.
     */
    private fun awaitUntil(timeoutMs: Long = 2000, condition: () -> Boolean) {
        val stopAt = System.currentTimeMillis() + timeoutMs
        while (!condition() && System.currentTimeMillis() < stopAt) {
            Thread.sleep(20)
        }
    }
}
