package com.meridiangoods.openorders

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
 * Fixture-level test proving [OpenOrdersConfiguration]'s wiring — a couple of representative
 * cases, not a re-run of every [OpenOrdersProjectionTest] scenario (see `axon-projection-testing`).
 *
 * Note: a `pooledStreaming` processor's `Coordinator` runs on a background thread even under
 * `AxonTestFixture` (confirmed by decompiling `axon-test-5.3.1.jar`: `then()` only awaits the
 * write-side publish, not processor catch-up) — see `w3-2-views.md` for the full note. Hence the
 * short poll below.
 */
class OpenOrdersProjectionFixtureTest {

    private lateinit var repository: InMemoryOpenOrdersRepository
    private lateinit var fixture: AxonTestFixture

    private val orderId = UUID.randomUUID()

    @BeforeEach
    fun beforeEach() {
        repository = InMemoryOpenOrdersRepository()
        val configurer = OpenOrdersConfiguration()
            .configure(PlaceOrderConfiguration().configure(EventSourcingConfigurer.create()))
            .componentRegistry { cr -> cr.registerComponent(OpenOrdersRepository::class.java) { repository } }
        fixture = AxonTestFixture.with(configurer)
    }

    @AfterEach
    fun afterEach() = fixture.stop()

    @Test
    fun `given a real Place Order command, when Order Placed is recorded, then a row appears via real wiring`() {
        val lineItems = listOf(OrderLine(sku = "SKU-1", quantity = 1, priceCents = 3000))

        fixture.given()
            .`when`()
            .command(PlaceOrder(orderId, UUID.randomUUID(), lineItems, totalCents = 3000))
            .then()
            .success()

        awaitUntil { repository.findByOrderId(orderId) != null }
        assertEquals(3000, repository.findByOrderId(orderId)?.totalCents)
    }

    @Test
    fun `given a placed order, when Payment Captured is folded directly, then the row updates via real wiring`() {
        val lineItems = listOf(OrderLine(sku = "SKU-1", quantity = 1, priceCents = 3000))

        fixture.given()
            .`when`()
            .command(PlaceOrder(orderId, UUID.randomUUID(), lineItems, totalCents = 3000))
            .then()
            .success()
        awaitUntil { repository.findByOrderId(orderId) != null }

        val capturedAt = Instant.now()
        fixture.given()
            .event(PaymentCaptured(UUID.randomUUID(), orderId, 3000, capturedAt, "PROV-REF-1"))
            .`when`()
            .nothing()
            .then()
            .noEvents()

        awaitUntil { repository.findByOrderId(orderId)?.capturedAt != null }
        assertEquals(capturedAt, repository.findByOrderId(orderId)?.capturedAt)
    }

    private fun awaitUntil(timeoutMs: Long = 2000, condition: () -> Boolean) {
        val stopAt = System.currentTimeMillis() + timeoutMs
        while (!condition() && System.currentTimeMillis() < stopAt) {
            Thread.sleep(20)
        }
    }
}
