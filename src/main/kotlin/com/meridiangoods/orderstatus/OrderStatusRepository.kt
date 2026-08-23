package com.meridiangoods.orderstatus

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Repository for the `Order Status` view. Upsert-keyed by `orderId` (INV-OS-1). */
interface OrderStatusRepository {
    fun upsert(row: OrderStatusRow)
    fun findByOrderId(orderId: UUID): OrderStatusRow?
    fun findAll(): List<OrderStatusRow>
}

class InMemoryOrderStatusRepository : OrderStatusRepository {
    private val store = ConcurrentHashMap<UUID, OrderStatusRow>()

    override fun upsert(row: OrderStatusRow) {
        store[row.orderId] = row
    }

    override fun findByOrderId(orderId: UUID): OrderStatusRow? = store[orderId]

    override fun findAll(): List<OrderStatusRow> = store.values.toList()
}
