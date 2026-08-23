package com.meridiangoods.paymentstorequest

import org.axonframework.messaging.queryhandling.annotation.QueryHandler

/**
 * Query: the full to-do list, per `slices/payments-to-request.md`'s description of this view as
 * "consumed by processor `Payment Requester`" — the query path that automation will dispatch
 * against once implemented.
 */
object ListPaymentsToRequest

class ListPaymentsToRequestQueryHandler(private val repository: PaymentsToRequestRepository) {

    @QueryHandler
    fun handle(query: ListPaymentsToRequest): List<PaymentsToRequestEntry> = repository.findAll()
}
