# Meridian Goods

![em status](status-badge.svg)

Meridian Goods is the [Slicewright](https://github.com/milehimikey/slicewright)
demo: a small ecommerce ordering system, event-modeled and event-sourced,
built slice by slice under the `em` contract — one slice, one branch, one PR,
each ratified before it was built. It's the proof artifact, not the pitch;
the methodology and its narrative live in the Slicewright repo linked above.

## Status at a glance
<!-- Generated — run `em status meridian-goods.em --tests src/test --md` to refresh the table,
     and `em status meridian-goods.em --tests src/test --badge -o status-badge.svg` to refresh
     the badge above; never hand-edit between the markers. -->
<!-- GENERATED:status:start -->
| Metric | Value |
|---|---|
| Slices | 8/8 implemented (0 ready-to-implement, 0 reviewed, 0 draft) |
| Invariants | 20/20 covered |
| Open issues | 0 |
| Open questions | 0 unchecked |
| Last conformed | `8f12ed8` — 10 commits and 0 slice-PRs behind HEAD |
<!-- GENERATED:status:end -->

## How to read this repo

- **The model** — [`meridian-goods.em`](meridian-goods.em) is the canonical
  source; [`meridian-goods.svg`](meridian-goods.svg) is its rendered
  diagram (see [Live view](#live-view) below to browse it interactively).
- **[`slices/`](slices/)** — one design doc per slice: fields, invariants,
  Given/When/Then scenarios, and a frontmatter `status`/`implementedIn`
  link back to the PR that built it.
- **The generated slice index** — the [Slices](#slices) table below is
  regenerated from the model and the slice docs; never hand-edit it (see
  the comment above the table).
- **[`conformance/`](conformance/)** — dated conformance reports. Each
  report documents both the run itself (what was checked, findings,
  coverage) *and* its ratification record (who ruled on each finding, and
  how) in one file — see
  [`conformance/2026-08-23-report.md`](conformance/2026-08-23-report.md)
  for the first completed cycle: 8 slices checked, all 20 distinct
  invariants coverage-verified (0 uncovered), 4 findings, all ratified
  same-day (the report's addendum explains its version-specific counts).
- **`walkthrough/*` tags** — the locked walkthrough script (in the
  Slicewright repo) reproduces against this repo's history via a series of
  `walkthrough/*` git tags, one per beat, each checked out from a clean
  clone.

## How to run it

```bash
./gradlew test
```

Modeling commands use pinned versions:

```bash
npx -y @milehimikey/em@1.10.0 validate meridian-goods.em
npx -y em-sdd-bridge@0.4.0 <slice-key> --symlink
```

## Honest notes

- **In-memory event store.** This repo runs Axon Framework 5's in-memory
  event store, not Axon Server — there is no GA Axon Server connector for
  Axon 5's DCB model yet. The event-sourcing mechanics are real; the
  persistence backend is not production infrastructure.
- **The drift→conform→ratify arc is real project history, not staged.**
  `Cancel Order` shipped a 24-hour post-capture grace window
  ([PR #17](https://github.com/milehimikey/meridian-goods/pull/17)) that
  contradicted its own ratified invariant; the first conformance run
  ([PR #18](https://github.com/milehimikey/meridian-goods/pull/18)) caught
  it, and it was ratified as an intentional business decision rather than
  reverted or silently ignored
  ([PR #19](https://github.com/milehimikey/meridian-goods/pull/19) /
  [#20](https://github.com/milehimikey/meridian-goods/pull/20)) — see the
  report linked above for the full record.

## Live view
While modeling, run the live view so the team can watch the diagram update:

```bash
em watch meridian-goods.em -o meridian-goods.svg --serve   # re-render + instant push-reload
# then open the URL it prints (http://localhost:5173/?svg=meridian-goods.svg) and share the screen
```

Pan/zoom to navigate the diagram (drag, scroll; **Fit** resets), and click **Review mode** in
the header for a slice-by-slice walkthrough. If a save fails to render, the viewer keeps the
last good diagram and shows an error banner until the next successful load.

Static render: `em render meridian-goods.em -o meridian-goods.svg`

## Patterns legend
- **State Change** — UI → Command → Event
- **State View** — Event(s) → Read Model → UI
- **Automation** — Read Model (slice before) → Processor + Command → Event, together
- **Translation** — External input (or Read Model, slice before) → Translation + Command → Event, together

Between them these are the only legal connections: `ui → command`, `command → event`,
`event → read model`, `read model → ui`, `read model → reaction`, `reaction → command`. A command
never reaches a read model directly — the event goes between them. Every slice is joined up at
both ends: something triggers each command (the screen it's issued from, or the reaction that
triggers it, also in this slice), and every event a command records is read by some read model — so each State
Change slice is paired with the State View slice that projects its event. A read model repeated
along the timeline (`view X again`) shows the same projection at a later point; the instances are
never connected to one another.

## Slices
<!-- The canonical slice index — the ONE place slices are enumerated (the state file
     points here rather than keeping its own copy). Generated — run
     `em slice index meridian-goods.em` to (re)write the table below from the model and its
     slice docs; never hand-edit between the markers. -->
<!-- GENERATED:slices:start -->
| # | Slice | Pattern | Status | Ratified by | Owner | Tracking | Implemented in | Design doc |
|---|-------|---------|--------|-------------|-------|----------|----------------|------------|
| 1 | Place Order | State Change | implemented | — | — | — | https://github.com/milehimikey/meridian-goods/pull/7 | [slices/place-order.md](slices/place-order.md) |
| 2 | Payments To Request | State View | implemented | — | — | — | https://github.com/milehimikey/meridian-goods/pull/8 | [slices/payments-to-request.md](slices/payments-to-request.md) |
| 3 | Request Payment | Automation | implemented | — | — | — | https://github.com/milehimikey/meridian-goods/pull/10 | [slices/request-payment.md](slices/request-payment.md) |
| 4 | Record Payment Result | Translation | implemented | — | — | — | https://github.com/milehimikey/meridian-goods/pull/12 | [slices/record-payment-result.md](slices/record-payment-result.md) |
| 5 | Open Orders | State View | implemented | — | — | — | https://github.com/milehimikey/meridian-goods/pull/9 | [slices/open-orders.md](slices/open-orders.md) |
| 6 | Order Status | State View | implemented | — | — | — | https://github.com/milehimikey/meridian-goods/pull/11 | [slices/order-status.md](slices/order-status.md) |
| 7 | Cancel Order | State Change | implemented | — | — | — | https://github.com/milehimikey/meridian-goods/pull/17 | [slices/cancel-order.md](slices/cancel-order.md) |
| 8 | Open Orders — cancelled | State View | implemented | — | — | — | https://github.com/milehimikey/meridian-goods/pull/16 | [slices/open-orders-cancelled.md](slices/open-orders-cancelled.md) |
<!-- GENERATED:slices:end -->

## Status
See [`.event-modeling.md`](.event-modeling.md) for current phase, decisions, and open questions.
