model "Meridian Goods — Ordering"

persona Customer
persona Staff
context Ordering
context Payments

slice "Place Order" {                      # State Change
  ui Storefront @Customer
  command Place Order
  event Order Placed @Ordering
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
  view Open Orders from "Order Placed", "Payment Captured"
  ui Fulfillment Dashboard @Staff
}

slice "Order Status" {                     # State View — customer-facing, closes all loops
  view Order Status from "Order Placed", "Payment Requested", "Payment Captured"
  ui Account Page @Customer
}
