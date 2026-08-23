package com.meridiangoods.placeorder

import org.axonframework.eventsourcing.annotation.EventSourcedEntity
import org.axonframework.eventsourcing.annotation.EventSourcingHandler
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator
import org.axonframework.messaging.commandhandling.annotation.CommandHandler
import org.axonframework.messaging.eventhandling.gateway.EventAppender
import org.axonframework.modelling.annotation.InjectEntity
import java.time.Clock
import java.time.Instant
import java.util.UUID

/**
 * Command handler for `Place Order` (`slices/place-order.md`). Decider idiom: [decide] is a pure
 * function of `(command, state) -> List<OrderPlaced>`; [handle] wires it to Axon.
 */
class PlaceOrderCommandHandler(
    private val clock: Clock = Clock.systemUTC(),
) {

    @CommandHandler
    fun handle(
        command: PlaceOrder,
        // Nullable: Place Order both creates and is the only writer of this entity, so no prior
        // state exists for a fresh orderId. A non-null `State` parameter here makes Axon's
        // @InjectEntity resolver use its FAIL strategy (EntityNotFoundException on a fresh id);
        // the nullable type switches it to RESOLVE_NULL, matching this slice's create-or-noop
        // shape.
        @InjectEntity(idProperty = "orderId") state: State?,
        eventAppender: EventAppender,
    ) {
        eventAppender.append(decide(command, state ?: State(), Instant.now(clock)))
    }

    /**
     * Pure decision function — no side effects, and every branch below is traceable to one
     * `INV-PO-n` in `slices/place-order.md`.
     */
    private fun decide(command: PlaceOrder, state: State, placedAt: Instant): List<OrderPlaced> {
        // INV-PO-1: idempotent placement by client-supplied orderId. An already-placed order's
        // orderId short-circuits every other check — same payload is a silent no-op, a
        // different payload is a rejected conflict. Neither case reaches INV-PO-2/3/4 below,
        // matching the doc: "that reuse is what makes idempotent placement meaningful."
        if (state.placed) {
            check(state.matches(command)) {
                "INV-PO-1: orderId ${command.orderId} is already recorded with a different payload " +
                    "(conflicting duplicate orderId)"
            }
            return emptyList()
        }

        // INV-PO-3: at least one line item.
        require(command.lineItems.isNotEmpty()) {
            "INV-PO-3: an order must contain at least one line item"
        }

        // INV-PO-4: every line item's quantity must be a positive integer.
        command.lineItems.forEach { line ->
            require(line.quantity > 0) {
                "INV-PO-4: line item '${line.sku}' has invalid quantity ${line.quantity}"
            }
        }

        // INV-PO-2: totalCents must equal Σ(priceCents × quantity); the server recomputes and
        // never trusts the client's total. Long arithmetic avoids overflow on the running sum.
        val computedTotalCents = command.lineItems.sumOf { it.priceCents.toLong() * it.quantity }
        check(computedTotalCents == command.totalCents.toLong()) {
            "INV-PO-2: totalCents ${command.totalCents} does not equal the computed total " +
                "$computedTotalCents over ${command.lineItems.size} line item(s)"
        }

        return listOf(
            OrderPlaced(
                orderId = command.orderId,
                customerId = command.customerId,
                lineItems = command.lineItems,
                totalCents = command.totalCents,
                placedAt = placedAt,
            ),
        )
    }

    // Mutable style, not the immutable data-class-plus-copy() style axon-entity-patterns
    // recommends by default: a data class @EntityCreator constructor with several defaulted
    // parameters fails at runtime against real axon-eventsourcing 5.3.1 —
    // AnnotationBasedEventSourcedEntityFactory resolves every *declared* constructor parameter
    // as an injectable "resource" via reflection, so it doesn't recognize Kotlin's synthetic
    // default-argument overload and throws (`No resource of type [boolean] has been
    // registered`). A genuinely zero-arg @EntityCreator constructor is required; see the
    // scaffold findings file for detail. This is a technical/infra correction, not a
    // doc-behavior decision.
    @EventSourcedEntity(tagKey = OrderTags.ORDER_ID)
    class State @EntityCreator constructor() {
        var placed: Boolean = false
            private set
        var customerId: UUID? = null
            private set
        var lineItems: List<OrderLine> = emptyList()
            private set
        var totalCents: Int? = null
            private set

        /** True when `command` would be a same-payload retry of the order already recorded. */
        fun matches(command: PlaceOrder): Boolean =
            customerId == command.customerId &&
                lineItems == command.lineItems &&
                totalCents == command.totalCents

        @EventSourcingHandler
        fun evolve(event: OrderPlaced) {
            placed = true
            customerId = event.customerId
            lineItems = event.lineItems
            totalCents = event.totalCents
        }
    }
}
