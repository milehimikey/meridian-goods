package com.meridiangoods.placeorder

import org.axonframework.modelling.annotation.TargetEntityId
import java.util.UUID

/**
 * Command: Place Order. See `slices/place-order.md`.
 *
 * `orderId` is client-generated (not server-assigned) — it doubles as the idempotency key for
 * INV-PO-1. Deliberately has no `placedAt` field: the slice doc lists `placedAt` on the *modeled*
 * command purely so `em validate`'s field-flow completeness check can trace `Order Placed.placedAt`
 * to a same-slice origin (see the doc's Command/Input table and Open Questions). The doc is
 * explicit that this is a modeling artifact, not a real request field — the actual API request
 * body excludes it, and [PlaceOrderCommandHandler] stamps the server clock itself.
 */
data class PlaceOrder(
    @TargetEntityId val orderId: UUID,
    val customerId: UUID,
    val lineItems: List<OrderLine>,
    val totalCents: Int,
)

/** One line of a `Place Order` command / `Order Placed` event — not independently identified. */
data class OrderLine(
    val sku: String,
    val quantity: Int,
    val priceCents: Int,
)

/** Tag-key constants for the `Order` identity this slice originates. */
object OrderTags {
    const val ORDER_ID = "orderId"
}
