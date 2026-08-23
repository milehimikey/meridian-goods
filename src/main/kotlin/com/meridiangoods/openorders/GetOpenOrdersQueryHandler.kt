package com.meridiangoods.openorders

import org.axonframework.messaging.queryhandling.annotation.QueryHandler

/** Query: the Staff-facing fulfillment queue, per `slices/open-orders.md`'s `Fulfillment Dashboard` UI. */
object GetOpenOrders

class GetOpenOrdersQueryHandler(private val repository: OpenOrdersRepository) {

    @QueryHandler
    fun handle(query: GetOpenOrders): List<OpenOrdersRow> = repository.findAll()
}
