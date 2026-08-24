# Domain Decisions

Short, dated-by-git-history record of cross-cutting calls made while modeling Meridian Goods'
ordering and payments slices. One bullet per decision, with rationale. These aren't ADRs — just
enough context that nobody re-litigates a call already made.

- **Money is represented as integer cents, never a float or decimal string.** Every money field
  (`totalCents`, `amountCents`, `priceCents`) is an integer count of the smallest currency unit.
  Rationale: floating-point currency math produces silent rounding drift; integer cents make
  `INV-PO-2` (total = Σ line items) an exact equality check, not an approximate one.

- **Single currency for the whole model.** No currency code field anywhere in the ordering or
  payments slices. Rationale: this is a demo of event-modeling mechanics, not a multi-currency
  commerce platform; adding a currency dimension would add fields and invariants (conversion,
  mixed-currency totals) that don't serve the walkthrough's teaching goal. Multi-currency is a
  documented non-goal, not an oversight.

- **One payment attempt per order.** The payments slices model exactly one
  `Payment Requested` → `Payment Captured` cycle per order; there is no retry-with-new-attempt
  loop, no partial capture, and no split payment. Rationale: keeps the Automation/Translation
  pattern demonstration (the payments slices) legible as a single reaction chain instead of a
  state machine with its own retry policy — a second payment attempt is a real feature but a
  distinct, deliberately out-of-scope slice.

- **`orderId` is client-generated, not server-assigned, specifically to make `Place Order`
  idempotent (`INV-PO-1`).** Rationale: a server-assigned ID can't be idempotent against retries
  — the client wouldn't know the ID to retry with. A client-generated UUID lets the client safely
  resubmit the exact same command after a timeout/dropped-connection and get the exact same
  result rather than a duplicate order. This is the walkthrough's canonical idempotency example.

- **Timestamp-origin convention: every event timestamp has a same-slice origin on its command,
  and the origin is stated honestly per field.** `Payment Captured.capturedAt` comes from the
  provider's webhook payload (the provider is the authoritative clock for capture);
  `Payment Requested.requestedAt` is stamped by the issuing automation (the processor authors
  the command, so it supplies the request time); `Order Placed.placedAt` is the one genuine
  server-clock field — it is echoed onto `Place Order` purely so the field-completeness check
  can trace it (the API request body excludes it; see the modeling note in
  `slices/place-order.md`). Rationale: keeps `em validate` at zero diagnostics and the
  `--slice-ready` handoff gate green without hiding where each value truly originates. Revisit
  if em grows a first-class system-assigned field marker.

- **Cancellation: deferred in the base model; added as the change-beat slice.** Originally
  deliberately deferred out of the base six-slice model — neither `Open Orders` nor `Order Status`
  had a "cancelled" state, and there was no `Cancel Order` command, so the base model could
  demonstrate the four core patterns cleanly without the repeated-view/state-machine complexity an
  order moving backward out of "open" brings. That deferral is now resolved: `slices/cancel-order.md`
  (State Change) and `slices/open-orders-cancelled.md` (the repeated `Open Orders` instance) model
  self-service cancellation as the walkthrough's CHANGE beat — introduced and taught on its own,
  as originally intended, rather than folded quietly into the base model. The load-bearing rule is
  **INV-CO-1 (v2, ratified 2026-08-23)**: an order can be cancelled *before* `Payment Captured`
  exists for it, **or within the 24-hour grace window after capture** — the provider auto-voids
  captures inside its settlement window, so no refund flow is needed there; beyond the window,
  cancellation is rejected. (v1 said before-capture only; the grace window shipped in PR #17
  ahead of the model, was caught by the first conformance run, and was ratified as an
  intentional business decision rather than reverted.)
