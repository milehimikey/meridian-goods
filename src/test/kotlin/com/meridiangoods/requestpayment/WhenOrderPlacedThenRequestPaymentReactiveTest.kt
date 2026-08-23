package com.meridiangoods.requestpayment

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
 * Reactive-path test for `Payment Requester` (`slices/request-payment.md`, "Happy path"
 * scenario): proves that `Order Placed` really does trigger the automation, which really does
 * dispatch `Request Payment` through the running `CommandDispatcher`/`CommandHandlingModule`
 * pipeline, producing a real `Payment Requested` event — not just that the command handler's
 * decision logic is correct in isolation (see [RequestPaymentCommandHandlerTest] for that).
 *
 * Per `axon-policy-testing`: the reaction is asynchronous (pooled-streaming processor), so the
 * assertion is wrapped in `.then().await { ... }` rather than asserted synchronously.
 */
class WhenOrderPlacedThenRequestPaymentReactiveTest {

    private lateinit var fixture: AxonTestFixture

    @BeforeEach
    fun beforeEach() {
        val configurer = RequestPaymentConfiguration().configure(EventSourcingConfigurer.create())
        fixture = AxonTestFixture.with(configurer)
    }

    @AfterEach
    fun afterEach() {
        fixture.stop()
    }

    @Test
    fun `happy path - given Order Placed for 5000, when the automation reacts, then Payment Requested is recorded for the same order and amount`() {
        val orderId = UUID.randomUUID()
        val orderPlaced = OrderPlaced(
            orderId = orderId,
            customerId = UUID.randomUUID(),
            lineItems = emptyList(),
            totalCents = 5000,
            placedAt = Instant.parse("2026-08-20T10:00:00Z"),
        )

        fixture.given()
            .events(orderPlaced)
            .then()
            .await { result ->
                result.eventsSatisfy { events ->
                    val paymentRequestedEvents = events.map { it.payload() }.filterIsInstance<PaymentRequested>()
                    assertTrue(paymentRequestedEvents.size == 1)
                    val event = paymentRequestedEvents.single()
                    assertTrue(event.orderId == orderId)
                    assertTrue(event.amountCents == 5000)
                }
            }
    }
}
