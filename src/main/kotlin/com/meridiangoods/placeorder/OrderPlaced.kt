package com.meridiangoods.placeorder

import org.axonframework.eventsourcing.annotation.EventTag
import java.time.Instant
import java.util.UUID

/**
 * Event: Order Placed. See `slices/place-order.md`.
 *
 * Read by (per the slice doc): `Payments To Request`, `Open Orders`, `Order Status` — all future
 * slices, none implemented yet. They will import this class directly from this package rather
 * than a shared `events` package; see the scaffold findings file for why that split was chosen.
 */
data class OrderPlaced(
    @EventTag(key = OrderTags.ORDER_ID) val orderId: UUID,
    val customerId: UUID,
    val lineItems: List<OrderLine>,
    val totalCents: Int,
    /** Server clock at the moment the event is recorded — see [PlaceOrder]'s doc comment. */
    val placedAt: Instant,
)
