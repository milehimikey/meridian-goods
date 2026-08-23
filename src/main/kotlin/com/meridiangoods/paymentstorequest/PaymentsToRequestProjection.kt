package com.meridiangoods.paymentstorequest

import com.meridiangoods.requestpayment.PaymentRequested

import com.meridiangoods.placeorder.OrderPlaced
import org.axonframework.messaging.core.annotation.SequencingPolicy
import org.axonframework.messaging.core.sequencing.PropertySequencingPolicy
import org.axonframework.messaging.eventhandling.annotation.EventHandler

/**
 * Projection for `Payments To Request` (`slices/payments-to-request.md`, pattern: state-view).
 * Plain class, no framework base type. Folds `Order Placed` into an upsert (INV-PTR-1) and
 * drains the queue on `Payment Requested` (INV-PTR-2).
 *
 * Deliberately has **no** handler for `Payment Captured` — per the doc's "Untouched by capture"
 * scenario, capture outcomes are not this view's concern; see
 * [PaymentsToRequestProjectionTest]'s reflection-based proof that no such handler exists.
 */
@SequencingPolicy(type = PropertySequencingPolicy::class, parameters = ["orderId"])
class PaymentsToRequestProjection(private val repository: PaymentsToRequestRepository) {

    /** INV-PTR-1: upsert keyed by orderId — folding the same `Order Placed` twice never duplicates. */
    @EventHandler
    fun on(event: OrderPlaced) {
        repository.upsert(
            PaymentsToRequestEntry(
                orderId = event.orderId,
                totalCents = event.totalCents,
                placedAt = event.placedAt,
            ),
        )
    }

    /** INV-PTR-2: removal on request — the queue drains once a request has been issued. */
    @EventHandler
    fun on(event: PaymentRequested) {
        repository.remove(event.orderId)
    }
}
