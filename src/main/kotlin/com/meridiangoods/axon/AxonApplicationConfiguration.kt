package com.meridiangoods.axon

import org.axonframework.common.configuration.AxonConfiguration
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer
import org.axonframework.messaging.commandhandling.gateway.CommandGateway
import org.axonframework.messaging.eventhandling.gateway.EventGateway
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * The one shared piece of Axon wiring in the whole application: it folds every discovered
 * [SliceModule] bean onto a single [EventSourcingConfigurer] and starts it. Slices register
 * themselves into this fold by existing as a `SliceModule` bean somewhere on the classpath —
 * this class never needs to know their names or packages, and therefore never needs to change
 * when a slice is added.
 *
 * There is no Axon Server connection registered here (see the ADR-less note in the project
 * README's "Axon Server connectivity" section): Axon Server support did not have a Maven-Central
 * release compatible with the pinned Axon Framework version at authoring time, so this scaffold
 * runs the framework's default in-memory event store. Every slice module and command handler is
 * written exactly as it would be against a DCB-capable store — the only difference is what's
 * plugged in as the [org.axonframework.eventsourcing.eventstore.EventStorageEngine].
 */
@Configuration
class AxonApplicationConfiguration(
    private val sliceModules: List<SliceModule>,
) {

    @Bean(destroyMethod = "shutdown")
    fun axonConfiguration(): AxonConfiguration {
        val configurer = sliceModules.fold(EventSourcingConfigurer.create()) { configurer, module ->
            module.configure(configurer)
        }
        return configurer.start()
    }

    @Bean
    fun commandGateway(axonConfiguration: AxonConfiguration): CommandGateway =
        axonConfiguration.getComponent(CommandGateway::class.java)

    @Bean
    fun eventGateway(axonConfiguration: AxonConfiguration): EventGateway =
        axonConfiguration.getComponent(EventGateway::class.java)
}
