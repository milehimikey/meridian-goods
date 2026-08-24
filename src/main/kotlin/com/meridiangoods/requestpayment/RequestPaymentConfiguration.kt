package com.meridiangoods.requestpayment

import com.meridiangoods.axon.SliceModule
import com.meridiangoods.paymentstorequest.PaymentsToRequestRepository
import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer
import org.axonframework.messaging.commandhandling.configuration.CommandHandlingModule
import org.axonframework.messaging.eventhandling.configuration.EventProcessorModule
import org.springframework.context.annotation.Configuration
import java.util.UUID

/**
 * Slice wiring for Request Payment (Automation: processor + triggered command + event, merged
 * per MIL-120). This is the *only* file that changes to bring the slice online — no other file
 * in the project needs to be edited to add this slice.
 */
@Configuration
class RequestPaymentConfiguration : SliceModule {

    override fun configure(configurer: EventSourcingConfigurer): EventSourcingConfigurer {
        val stateEntity = EventSourcedEntityModule.autodetected(
            UUID::class.java,
            RequestPaymentCommandHandler.State::class.java,
        )

        val commandHandlingModule = CommandHandlingModule
            .named("RequestPayment")
            .commandHandlers()
            .autodetectedCommandHandlingComponent { RequestPaymentCommandHandler() }

        val eventProcessor = EventProcessorModule
            .pooledStreaming("WhenOrderPlacedThenRequestPaymentFromQueue")
            .eventHandlingComponents { c ->
                c.autodetected { cfg ->
                    WhenOrderPlacedThenRequestPaymentFromQueue(cfg.getComponent(PaymentsToRequestRepository::class.java))
                }
            }
            .notCustomized()

        return configurer
            .registerEntity(stateEntity)
            .registerCommandHandlingModule(commandHandlingModule)
            .messaging { it.eventProcessing { ep -> ep.pooledStreaming { ps -> ps.processor(eventProcessor) } } }
    }
}
