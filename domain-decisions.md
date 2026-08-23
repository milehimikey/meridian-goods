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

- **Cancellation is deliberately deferred to a future slice, not modeled here.** Neither
  `Open Orders` nor `Order Status` has a "cancelled" state, and there's no `Cancel Order` command
  in this base model. Rationale: the base six-slice model demonstrates the four core patterns
  cleanly; cancellation (and the repeated-view/state-machine complexity it brings — an order
  that can move backward out of "open") is reserved for a dedicated change-beat slice later in
  the walkthrough, so it can be introduced and taught on its own rather than folded quietly into
  the base model.
