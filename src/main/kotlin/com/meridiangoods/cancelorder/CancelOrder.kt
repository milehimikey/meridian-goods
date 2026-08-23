package com.meridiangoods.cancelorder

import org.axonframework.modelling.annotation.TargetEntityId
import java.util.UUID

/**
 * Command: Cancel Order. See `slices/cancel-order.md`.
 *
 * Deliberately has no `cancelledAt` field: like `Place Order`'s `placedAt`
 * (`com.meridiangoods.placeorder.PlaceOrder`), the doc lists `cancelledAt` on the *modeled*
 * command purely so `em validate`'s field-flow completeness check can trace
 * `Order Cancelled.cancelledAt` to a same-slice origin (see the doc's Command/Input table). The
 * real API request excludes it; [CancelOrderCommandHandler] stamps the server clock itself.
 */
data class CancelOrder(
    @TargetEntityId val orderId: UUID,
    val customerId: UUID,
)
