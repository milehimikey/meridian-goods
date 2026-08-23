package com.meridiangoods.openorders

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Repository for the `Open Orders` view. Upsert-keyed by `orderId` (INV-OO-1).
 *
 * `remove`/`isCancelled`/`markCancelled` back the repeated `Open Orders` instance's removal fold
 * (`slices/open-orders-cancelled.md`, INV-OOC-1/INV-OOC-2) — see [OpenOrdersProjection]'s
 * `on(OrderCancelled)` handler. `isCancelled`/`markCancelled` exist only to support that doc's
 * "out-of-order delivery" alternate flow: a tombstone so a `Order Cancelled` that arrives before
 * its order's `Order Placed` can suppress the row `on(OrderPlaced)` would otherwise (re)create.
 */
interface OpenOrdersRepository {
    fun upsert(row: OpenOrdersRow)
    fun findByOrderId(orderId: UUID): OpenOrdersRow?
    fun findAll(): List<OpenOrdersRow>
    fun remove(orderId: UUID)
    fun isCancelled(orderId: UUID): Boolean
    fun markCancelled(orderId: UUID)
}

class InMemoryOpenOrdersRepository : OpenOrdersRepository {
    private val store = ConcurrentHashMap<UUID, OpenOrdersRow>()
    private val cancelled = ConcurrentHashMap.newKeySet<UUID>()

    override fun upsert(row: OpenOrdersRow) {
        store[row.orderId] = row
    }

    override fun findByOrderId(orderId: UUID): OpenOrdersRow? = store[orderId]

    override fun findAll(): List<OpenOrdersRow> = store.values.toList()

    override fun remove(orderId: UUID) {
        store.remove(orderId)
    }

    override fun isCancelled(orderId: UUID): Boolean = cancelled.contains(orderId)

    override fun markCancelled(orderId: UUID) {
        cancelled.add(orderId)
    }
}
