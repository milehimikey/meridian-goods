package com.meridiangoods.cancelorder

import org.axonframework.eventsourcing.annotation.EventTag
import java.time.Instant
import java.util.UUID

/**
 * Event: Order Cancelled. See `slices/cancel-order.md`.
 *
 * Tagged with `orderId` — the same tag `Order Placed` and `Payment Captured` carry, so this
 * slice's own [CancelOrderCommandHandler.State] can see all three event types in one DCB
 * consistency boundary (see that class's `@EventCriteriaBuilder`), and so `Open Orders —
 * cancelled` (`slices/open-orders-cancelled.md`) can fold on it by the same key the earlier
 * `Open Orders` instance uses.
 *
 * The tag key is a literal string, not a constant imported from `com.meridiangoods.placeorder` —
 * same convention `com.meridiangoods.recordpaymentresult.PaymentCaptured` already uses for its
 * own `orderId` tag, to avoid a needless cross-package dependency for a single string literal.
 *
 * NOTE — deliberate cross-PR byte-for-byte duplication (same pattern as w3-3's
 * `PaymentRequested`/`PaymentCaptured` precedent, `findings/w3-3-reactions.md`): this exact file
 * is committed identically into both the `impl-cancel-order` branch (its canonical home, this
 * slice) and the `impl-open-orders-cancelled` branch (which needs this class to compile its
 * removal fold on `com.meridiangoods.openorders.OpenOrdersProjection` before `impl-cancel-order`
 * merges — neither branch stacks on the other). Both copies are authored from the same `em
 * export --slice cancel-order` field contract, so a merge of both PRs converges on identical
 * content at this path with no conflict.
 */
data class OrderCancelled(
    @EventTag(key = "orderId") val orderId: UUID,
    val customerId: UUID,
    val cancelledAt: Instant,
)
