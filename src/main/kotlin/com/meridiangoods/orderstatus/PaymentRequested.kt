package com.meridiangoods.orderstatus

import java.time.Instant
import java.util.UUID

/**
 * Local placeholder for the `Payment Requested` event (owned by the `Request Payment` slice —
 * package `com.meridiangoods.requestpayment`). See `PaymentsToRequestProjection`'s identical
 * note in `com.meridiangoods.paymentstorequest` for the full rationale: that slice is a
 * parallel, independently-branched wave not yet merged, so `Order Status` — a pure reader —
 * cannot import the real class.
 *
 * **Provisional stand-in, not the canonical class** — see `w3-2-views.md`, "Flag: cross-branch
 * upstream-event placeholders", for the merge-time reconciliation this file requires.
 */
data class PaymentRequested(
    val paymentId: UUID,
    val orderId: UUID,
    val amountCents: Int,
    val requestedAt: Instant,
)
