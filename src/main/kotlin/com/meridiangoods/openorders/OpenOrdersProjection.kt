package com.meridiangoods.openorders

import com.meridiangoods.recordpaymentresult.PaymentCaptured

import com.meridiangoods.cancelorder.OrderCancelled
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
 *
 * `on(OrderCancelled)` is the repeated-instance fold from `slices/open-orders-cancelled.md`
 * ("`Open Orders` again from `Order Cancelled`"). **Placement call, recorded per the task
 * contract**: this handler lives on the *existing* `OpenOrdersProjection` class rather than a
 * new class/package, because it is the same read model (the repeated-view rule: a later instance
 * of `Open Orders` is never a new view, just another fold on the same one) and because
 * `axon-projection-patterns`' own reference shape (`CoursesStatsProjection`) is exactly this:
 * one class, several `@EventHandler` methods, one repository, one read model, growing new
 * `on(...)` overloads as new events feed it — this class already does that for `OrderPlaced` and
 * `PaymentCaptured` from two other slices' packages. Sanctioned shared-file edit per this
 * engagement's ownership grant; no new `com.meridiangoods.openorderscancelled` package was
 * created since there is nothing slice-specific left to wire — no new processor, no new
 * repository, no new query handler.
 */
@SequencingPolicy(type = PropertySequencingPolicy::class, parameters = ["orderId"])
class OpenOrdersProjection(private val repository: OpenOrdersRepository) {

    /**
     * INV-OO-1: upsert keyed by orderId; preserves a `capturedAt` set by an earlier-arriving
     * `Payment Captured`. Also honors `open-orders-cancelled.md`'s "out-of-order delivery"
     * alternate flow: if `Order Cancelled` already tombstoned this orderId (arrived first), this
     * handler skips (re)creating a row for it.
     */
    @EventHandler
    fun on(event: OrderPlaced) {
        if (repository.isCancelled(event.orderId)) {
            return
        }
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

    /**
     * `open-orders-cancelled.md`, INV-OOC-1: removes (not flags) the row — a cancelled order
     * carries no further fulfillment action for staff. INV-OOC-2: idempotent under redelivery —
     * removing an already-absent row, and re-marking an already-cancelled orderId, are both
     * no-ops on the underlying store. Also marks the orderId cancelled regardless of whether a
     * row existed yet, so the "out-of-order delivery" alternate flow above is honored even if
     * `Order Cancelled` is the very first event this projection ever sees for this orderId.
     */
    @EventHandler
    fun on(event: OrderCancelled) {
        repository.remove(event.orderId)
        repository.markCancelled(event.orderId)
    }
}
