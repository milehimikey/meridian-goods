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
  view Payments To Request from "Order Placed" {
    orderId: UUID
    totalCents: Int
    placedAt: DateTime
  } note "slices/payments-to-request.md"
}

slice "Request Payment" {                  # Automation: reaction + command + event together
  processor Payment Requester from "Payments To Request" note "slices/request-payment.md"
  command Request Payment {
    paymentId: UUID
    orderId: UUID
    amountCents: Int
  }
  event Payment Requested @Payments {
    paymentId: UUID
    orderId: UUID
    amountCents: Int
    requestedAt: DateTime
  }
}

slice "Record Payment Result" {            # Translation: provider webhook, externally triggered, one slice
  translation Payment Provider Adapter note "slices/record-payment-result.md"
  command Record Payment Result {
    paymentId: UUID
    orderId: UUID
    amountCents: Int
    providerRef: String
  }
  event Payment Captured @Payments {
    paymentId: UUID
    orderId: UUID
    amountCents: Int
    capturedAt: DateTime
    providerRef: String
  }
}

slice "Open Orders" {                      # State View — staff-facing
  view Open Orders from "Order Placed", "Payment Captured"
  ui Fulfillment Dashboard @Staff
}

slice "Order Status" {                     # State View — customer-facing, closes all loops
  view Order Status from "Order Placed", "Payment Requested", "Payment Captured"
  ui Account Page @Customer
}
