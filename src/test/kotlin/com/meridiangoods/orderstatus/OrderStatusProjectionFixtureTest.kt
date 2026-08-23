package com.meridiangoods.orderstatus

import com.meridiangoods.placeorder.OrderLine
import com.meridiangoods.placeorder.PlaceOrder
import com.meridiangoods.placeorder.PlaceOrderConfiguration
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer
import org.axonframework.test.fixture.AxonTestFixture
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals

/**
 * Fixture-level test proving [OrderStatusConfiguration]'s wiring — a couple of representative
 * cases, not a re-run of every [OrderStatusProjectionTest] scenario (see
 * `axon-projection-testing`).
 *
 * Note: a `pooledStreaming` processor's `Coordinator` runs on a background thread even under
 * `AxonTestFixture` (confirmed by decompiling `axon-test-5.3.1.jar`: `then()` only awaits the
 * write-side publish, not processor catch-up) — see `w3-2-views.md` for the full note. Hence the
 * short poll below.
 */
class OrderStatusProjectionFixtureTest {

    private lateinit var repository: InMemoryOrderStatusRepository
    private lateinit var fixture: AxonTestFixture

    private val orderId = UUID.randomUUID()

    @BeforeEach
    fun beforeEach() {
        repository = InMemoryOrderStatusRepository()
        val configurer = OrderStatusConfiguration()
            .configure(PlaceOrderConfiguration().configure(EventSourcingConfigurer.create()))
            .componentRegistry { cr -> cr.registerComponent(OrderStatusRepository::class.java) { repository } }
        fixture = AxonTestFixture.with(configurer)
    }

    @AfterEach
    fun afterEach() = fixture.stop()

    @Test
    fun `given a real Place Order command, when Order Placed is recorded, then the status row reads placed via real wiring`() {
        val lineItems = listOf(OrderLine(sku = "SKU-1", quantity = 1, priceCents = 4000))

        fixture.given()
            .`when`()
            .command(PlaceOrder(orderId, UUID.randomUUID(), lineItems, totalCents = 4000))
            .then()
            .success()

        awaitUntil { repository.findByOrderId(orderId) != null }
        assertEquals(OrderStatus.PLACED, repository.findByOrderId(orderId)?.status)
    }

    @Test
    fun `given a placed order, when Payment Requested then Payment Captured are folded directly, then status progresses via real wiring`() {
        val lineItems = listOf(OrderLine(sku = "SKU-1", quantity = 1, priceCents = 4000))

        fixture.given()
            .`when`()
            .command(PlaceOrder(orderId, UUID.randomUUID(), lineItems, totalCents = 4000))
            .then()
            .success()
        awaitUntil { repository.findByOrderId(orderId) != null }

        fixture.given()
            .event(PaymentRequested(UUID.randomUUID(), orderId, 4000, Instant.now()))
            .`when`()
            .nothing()
            .then()
            .noEvents()
        awaitUntil { repository.findByOrderId(orderId)?.status == OrderStatus.PAYMENT_REQUESTED }
        assertEquals(OrderStatus.PAYMENT_REQUESTED, repository.findByOrderId(orderId)?.status)

        fixture.given()
            .event(PaymentCaptured(UUID.randomUUID(), orderId, 4000, Instant.now(), "PROV-REF-1"))
            .`when`()
            .nothing()
            .then()
            .noEvents()
        awaitUntil { repository.findByOrderId(orderId)?.status == OrderStatus.PAID }
        assertEquals(OrderStatus.PAID, repository.findByOrderId(orderId)?.status)
    }

    private fun awaitUntil(timeoutMs: Long = 2000, condition: () -> Boolean) {
        val stopAt = System.currentTimeMillis() + timeoutMs
        while (!condition() && System.currentTimeMillis() < stopAt) {
            Thread.sleep(20)
        }
    }
}
