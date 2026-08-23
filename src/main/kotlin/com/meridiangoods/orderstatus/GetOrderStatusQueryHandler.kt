package com.meridiangoods.orderstatus

import org.axonframework.messaging.queryhandling.annotation.QueryHandler
import java.util.UUID

/** Query: a single order's status, per `slices/order-status.md`'s `Account Page` UI. */
data class GetOrderStatus(val orderId: UUID)

class GetOrderStatusQueryHandler(private val repository: OrderStatusRepository) {

    @QueryHandler
    fun handle(query: GetOrderStatus): OrderStatusRow? = repository.findByOrderId(query.orderId)
}
