package com.meridiangoods.paymentstorequest

import java.time.Instant
import java.util.UUID

/**
 * Read model row for `Payments To Request` (`slices/payments-to-request.md`): one to-do entry
 * per order that has been placed but not yet had a payment request issued.
 */
data class PaymentsToRequestEntry(
    val orderId: UUID,
    val totalCents: Int,
    val placedAt: Instant,
)
