package com.meridiangoods.openorders

import java.time.Instant
import java.util.UUID

/**
 * Local placeholder for the `Payment Captured` event (owned by the `Record Payment Result`
 * slice — package `com.meridiangoods.recordpaymentresult` per this project's per-slice
 * event-ownership convention, see the scaffold findings file
 * `w3-1-scaffold-place-order.md`). That slice is a Translation implemented in a parallel,
 * independently-branched wave of this engagement and is not merged (or even branched) as of
 * this PR, so `Open Orders` — a pure reader of the event — cannot import the real class.
 *
 * Field shape matches `meridian-goods.em`'s `Payment Captured` event exactly (`paymentId`,
 * `orderId`, `amountCents`, `capturedAt`, `providerRef`).
 *
 * **This is a provisional stand-in, not the canonical class.** When the `Record Payment Result`
 * slice merges, whoever reconciles the two branches must delete this file and repoint
 * [OpenOrdersProjection] at the real
 * `com.meridiangoods.recordpaymentresult.PaymentCaptured` — otherwise this projection silently
 * never fires against the real published event (Axon dispatches `@EventHandler` methods by
 * concrete payload class). See the findings file `w3-2-views.md`, "Flag: cross-branch
 * upstream-event placeholders", for the full note.
 */
data class PaymentCaptured(
    val paymentId: UUID,
    val orderId: UUID,
    val amountCents: Int,
    val capturedAt: Instant,
    val providerRef: String,
)
