package com.meridiangoods.openorders

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Repository for the `Open Orders` view. Upsert-keyed by `orderId` (INV-OO-1). */
interface OpenOrdersRepository {
    fun upsert(row: OpenOrdersRow)
    fun findByOrderId(orderId: UUID): OpenOrdersRow?
    fun findAll(): List<OpenOrdersRow>
}

class InMemoryOpenOrdersRepository : OpenOrdersRepository {
    private val store = ConcurrentHashMap<UUID, OpenOrdersRow>()

    override fun upsert(row: OpenOrdersRow) {
        store[row.orderId] = row
    }

    override fun findByOrderId(orderId: UUID): OpenOrdersRow? = store[orderId]

    override fun findAll(): List<OpenOrdersRow> = store.values.toList()
}
