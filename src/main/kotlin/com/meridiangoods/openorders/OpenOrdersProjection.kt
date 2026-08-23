package com.meridiangoods.openorders

import com.meridiangoods.recordpaymentresult.PaymentCaptured

import com.meridiangoods.placeorder.OrderPlaced
import org.axonframework.messaging.core.annotation.SequencingPolicy
import org.axonframework.messaging.core.sequencing.PropertySequencingPolicy
import org.axonframework.messaging.eventhandling.annotation.EventHandler

/**
 * Projection for `Open Orders` (`slices/open-orders.md`, pattern: state-view). Plain class, no
 * framework base type. Both handlers upsert-in-place keyed by `orderId` (INV-OO-1) and are
 * defensive about ordering: whichever of `Order Placed` / `Payment Captured` arrives first
 * creates the row, and the other fills in the rest — the doc's documented "out-of-order
 * delivery" alternate flow, not just the happy path.
 */
@SequencingPolicy(type = PropertySequencingPolicy::class, parameters = ["orderId"])
class OpenOrdersProjection(private val repository: OpenOrdersRepository) {

    /** INV-OO-1: upsert keyed by orderId; preserves a `capturedAt` set by an earlier-arriving `Payment Captured`. */
    @EventHandler
    fun on(event: OrderPlaced) {
        val existing = repository.findByOrderId(event.orderId)
        repository.upsert(
            OpenOrdersRow(
                orderId = event.orderId,
                customerId = event.customerId,
                totalCents = event.totalCents,
                placedAt = event.placedAt,
                capturedAt = existing?.capturedAt,
            ),
        )
    }

    /**
     * INV-OO-2: `capturedAt` is populated only when this event has actually been projected —
     * never inferred. INV-OO-1: upsert keyed by orderId; if `Order Placed` hasn't landed yet
     * (out-of-order delivery), creates a partial row that `on(OrderPlaced)` later fills in.
     */
    @EventHandler
    fun on(event: PaymentCaptured) {
        val existing = repository.findByOrderId(event.orderId)
        repository.upsert(
            OpenOrdersRow(
                orderId = event.orderId,
                customerId = existing?.customerId,
                totalCents = existing?.totalCents,
                placedAt = existing?.placedAt,
                capturedAt = event.capturedAt,
            ),
        )
    }
}
