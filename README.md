# Meridian Goods

Meridian Goods is a small ecommerce ordering system, modeled and implemented as a demonstration of event modeling and event sourcing end to end.

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
| # | Slice | Pattern | Status | Implemented in | Design doc |
|---|-------|---------|--------|----------------|------------|
| 1 | Place Order | State Change | implemented | https://github.com/milehimikey/meridian-goods/pull/7 | [slices/place-order.md](slices/place-order.md) |
| 2 | Payments To Request | State View | implemented | https://github.com/milehimikey/meridian-goods/pull/8 | [slices/payments-to-request.md](slices/payments-to-request.md) |
| 3 | Request Payment | Automation | implemented | https://github.com/milehimikey/meridian-goods/pull/10 | [slices/request-payment.md](slices/request-payment.md) |
| 4 | Record Payment Result | Translation | implemented | https://github.com/milehimikey/meridian-goods/pull/12 | [slices/record-payment-result.md](slices/record-payment-result.md) |
| 5 | Open Orders | State View | implemented | https://github.com/milehimikey/meridian-goods/pull/9 | [slices/open-orders.md](slices/open-orders.md) |
| 6 | Order Status | State View | implemented | https://github.com/milehimikey/meridian-goods/pull/11 | [slices/order-status.md](slices/order-status.md) |
| 7 | Cancel Order | State Change | draft | — | [slices/cancel-order.md](slices/cancel-order.md) |
| 8 | Open Orders — cancelled | State View | draft | — | [slices/open-orders-cancelled.md](slices/open-orders-cancelled.md) |
<!-- GENERATED:slices:end -->

## Status
See [`.event-modeling.md`](.event-modeling.md) for current phase, decisions, and open questions.
