package com.meridiangoods.orderstatus

import com.meridiangoods.recordpaymentresult.PaymentCaptured

import com.meridiangoods.requestpayment.PaymentRequested

import com.meridiangoods.placeorder.OrderPlaced
import org.axonframework.messaging.core.annotation.SequencingPolicy
import org.axonframework.messaging.core.sequencing.PropertySequencingPolicy
import org.axonframework.messaging.eventhandling.annotation.EventHandler

/**
 * Projection for `Order Status` (`slices/order-status.md`, pattern: state-view). Plain class, no
 * framework base type. Every handler upserts-in-place keyed by `orderId` (INV-OS-1) and sets
 * only its own timestamp field, leaving the others as whatever was already present — never a
 * separately stored `status` (INV-OS-2; see [OrderStatusRow.status]'s computed derivation).
 */
@SequencingPolicy(type = PropertySequencingPolicy::class, parameters = ["orderId"])
class OrderStatusProjection(private val repository: OrderStatusRepository) {

    /** INV-OS-1: upsert keyed by orderId; preserves any timestamps already set by later-modeled events arriving out of order. */
    @EventHandler
    fun on(event: OrderPlaced) {
        val existing = repository.findByOrderId(event.orderId)
        repository.upsert(
            OrderStatusRow(
                orderId = event.orderId,
                placedAt = event.placedAt,
                requestedAt = existing?.requestedAt,
                capturedAt = existing?.capturedAt,
            ),
        )
    }

    /** INV-OS-1 / INV-OS-2: sets only `requestedAt`; "payment requested" is a derived read, not a stored fact. */
    @EventHandler
    fun on(event: PaymentRequested) {
        val existing = repository.findByOrderId(event.orderId)
        repository.upsert(
            OrderStatusRow(
                orderId = event.orderId,
                placedAt = existing?.placedAt,
                requestedAt = event.requestedAt,
                capturedAt = existing?.capturedAt,
            ),
        )
    }

    /** INV-OS-1 / INV-OS-2: sets only `capturedAt`; "paid" is a derived read, not a stored fact. */
    @EventHandler
    fun on(event: PaymentCaptured) {
        val existing = repository.findByOrderId(event.orderId)
        repository.upsert(
            OrderStatusRow(
                orderId = event.orderId,
                placedAt = existing?.placedAt,
                requestedAt = existing?.requestedAt,
                capturedAt = event.capturedAt,
            ),
        )
    }
}
