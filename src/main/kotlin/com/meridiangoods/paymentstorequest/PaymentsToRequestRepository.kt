package com.meridiangoods.paymentstorequest

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Repository for the `Payments To Request` view. Upsert-keyed by `orderId` (INV-PTR-1) with
 * explicit removal on request (INV-PTR-2) — this is a draining to-do queue, not an append-only
 * log.
 */
interface PaymentsToRequestRepository {
    fun upsert(entry: PaymentsToRequestEntry)
    fun remove(orderId: UUID)
    fun findByOrderId(orderId: UUID): PaymentsToRequestEntry?
    fun findAll(): List<PaymentsToRequestEntry>
}

class InMemoryPaymentsToRequestRepository : PaymentsToRequestRepository {
    private val store = ConcurrentHashMap<UUID, PaymentsToRequestEntry>()

    override fun upsert(entry: PaymentsToRequestEntry) {
        store[entry.orderId] = entry
    }

    override fun remove(orderId: UUID) {
        store.remove(orderId)
    }

    override fun findByOrderId(orderId: UUID): PaymentsToRequestEntry? = store[orderId]

    override fun findAll(): List<PaymentsToRequestEntry> = store.values.toList()
}
