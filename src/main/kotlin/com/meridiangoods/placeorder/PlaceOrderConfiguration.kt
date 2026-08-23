package com.meridiangoods.placeorder

import com.meridiangoods.axon.SliceModule
import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer
import org.axonframework.messaging.commandhandling.configuration.CommandHandlingModule
import org.springframework.context.annotation.Configuration
import java.util.UUID

/**
 * Slice wiring for Place Order. This is the *only* file that changes to bring the slice online —
 * it registers itself as a [SliceModule] Spring bean, discovered by
 * [com.meridiangoods.axon.AxonApplicationConfiguration]'s component scan. No other file in the
 * project needs to be edited to add this slice.
 */
@Configuration
class PlaceOrderConfiguration : SliceModule {

    override fun configure(configurer: EventSourcingConfigurer): EventSourcingConfigurer {
        val stateEntity = EventSourcedEntityModule.autodetected(
            UUID::class.java,
            PlaceOrderCommandHandler.State::class.java,
        )

        val commandHandlingModule = CommandHandlingModule
            .named("PlaceOrder")
            .commandHandlers()
            .autodetectedCommandHandlingComponent { PlaceOrderCommandHandler() }

        return configurer
            .registerEntity(stateEntity)
            .registerCommandHandlingModule(commandHandlingModule)
    }
}
