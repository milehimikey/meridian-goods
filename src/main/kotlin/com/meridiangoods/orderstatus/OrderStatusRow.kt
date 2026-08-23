package com.meridiangoods.orderstatus

import java.time.Instant
import java.util.UUID

/**
 * Read model row for `Order Status` (`slices/order-status.md`): the Customer-facing status of
 * a single order.
 *
 * INV-OS-2: "status" is deliberately **not** a stored field — [status] is a computed property
 * derived purely from which timestamp fields are actually populated, recalculated on every
 * read. There is no independent enum that could drift out of sync with the underlying event
 * facts.
 */
data class OrderStatusRow(
    val orderId: UUID,
    val placedAt: Instant?,
    val requestedAt: Instant? = null,
    val capturedAt: Instant? = null,
) {
    val status: OrderStatus
        get() = when {
            capturedAt != null -> OrderStatus.PAID
            requestedAt != null -> OrderStatus.PAYMENT_REQUESTED
            else -> OrderStatus.PLACED
        }
}

enum class OrderStatus {
    PLACED,
    PAYMENT_REQUESTED,
    PAID,
}
