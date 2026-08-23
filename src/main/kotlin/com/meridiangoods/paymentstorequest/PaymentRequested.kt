package com.meridiangoods.paymentstorequest

import java.time.Instant
import java.util.UUID

/**
 * Local placeholder for the `Payment Requested` event (owned by the `Request Payment` slice —
 * package `com.meridiangoods.requestpayment` per this project's per-slice event-ownership
 * convention, see the scaffold findings file `w3-1-scaffold-place-order.md`). That slice is an
 * Automation implemented in a parallel, independently-branched wave of this engagement and is
 * not merged (or even branched) as of this PR, so `Payments To Request` — a pure reader of the
 * event — cannot import the real class.
 *
 * Field shape matches `meridian-goods.em`'s `Payment Requested` event exactly (`paymentId`,
 * `orderId`, `amountCents`, `requestedAt`).
 *
 * **This is a provisional stand-in, not the canonical class.** When the `Request Payment` slice
 * merges, whoever reconciles the two branches must delete this file and repoint
 * [PaymentsToRequestProjection] at the real `com.meridiangoods.requestpayment.PaymentRequested`
 * — otherwise this projection silently never fires against the real published event (Axon
 * dispatches `@EventHandler` methods by concrete payload class). See the findings file
 * `w3-2-views.md`, "Flag: cross-branch upstream-event placeholders", for the full note.
 */
data class PaymentRequested(
    val paymentId: UUID,
    val orderId: UUID,
    val amountCents: Int,
    val requestedAt: Instant,
)
