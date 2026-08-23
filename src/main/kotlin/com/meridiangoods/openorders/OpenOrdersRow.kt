package com.meridiangoods.openorders

import java.time.Instant
import java.util.UUID

/**
 * Read model row for `Open Orders` (`slices/open-orders.md`): the Staff-facing fulfillment
 * queue of placed orders. `capturedAt` stays genuinely `null` until a `Payment Captured` event
 * has actually been projected for this order (INV-OO-2) — never backfilled, defaulted, or
 * inferred.
 */
data class OpenOrdersRow(
    val orderId: UUID,
    val customerId: UUID?,
    val totalCents: Int?,
    val placedAt: Instant?,
    val capturedAt: Instant?,
)
