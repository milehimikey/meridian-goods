model "Meridian Goods — Ordering"

persona Customer
persona Staff
context Ordering
context Payments

type OrderLine {
  sku: String
  quantity: Int
  priceCents: Int
}

slice "Place Order" {                      # State Change
  ui Storefront @Customer
  command Place Order {
    orderId: UUID
    customerId: UUID
    lineItems: OrderLine[]
    totalCents: Int
    placedAt: DateTime
  } note "slices/place-order.md"
  event Order Placed @Ordering {
    orderId: UUID
    customerId: UUID
    lineItems: OrderLine[]
    totalCents: Int
    placedAt: DateTime
  }
}

slice "Payments To Request" {              # Automation's read model (consumed by next slice's processor)
  view Payments To Request from "Order Placed"
}

slice "Request Payment" {                  # Automation: reaction + command + event together
  processor Payment Requester from "Payments To Request"
  command Request Payment
  event Payment Requested @Payments
}

slice "Record Payment Result" {            # Translation: provider webhook, externally triggered, one slice
  translation Payment Provider Adapter
  command Record Payment Result
  event Payment Captured @Payments
}

slice "Open Orders" {                      # State View — staff-facing
  view Open Orders from "Order Placed", "Payment Captured" {
    orderId: UUID
    customerId: UUID
    totalCents: Int
    placedAt: DateTime
    capturedAt: DateTime
  } note "slices/open-orders.md"
  ui Fulfillment Dashboard @Staff
}

slice "Order Status" {                     # State View — customer-facing, closes all loops
  view Order Status from "Order Placed", "Payment Requested", "Payment Captured" {
    orderId: UUID
    placedAt: DateTime
    requestedAt: DateTime
    capturedAt: DateTime
  } note "slices/order-status.md"
  ui Account Page @Customer
}
