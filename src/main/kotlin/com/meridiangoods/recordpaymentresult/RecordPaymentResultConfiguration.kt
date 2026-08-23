package com.meridiangoods.recordpaymentresult

import com.meridiangoods.axon.SliceModule
import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer
import org.axonframework.messaging.commandhandling.configuration.CommandHandlingModule
import org.springframework.context.annotation.Configuration
import java.util.UUID

/**
 * Slice wiring for Record Payment Result (Translation: externally-triggered adapter + triggered
 * command + event, merged per MIL-120). This is the *only* file that changes to bring the
 * slice's command-handling side online — no other file in the project needs to be edited.
 *
 * [PaymentProviderAdapter] is deliberately **not** wired here as a Spring bean: per
 * `axon-translation-patterns`, an externally-triggered translation has no `EventProcessorModule`
 * to register, and this model has no `ui`/HTTP endpoint slice for the webhook yet (the doc's
 * Trigger & Actor section is explicit that the webhook call itself is the input, with "no
 * durable artifact" and no `from`). A real webhook controller, when one is modeled, would
 * construct `PaymentProviderAdapter(commandGateway)` with the `CommandGateway` from the
 * application's `AxonConfiguration`/Spring context. Until then this is a known, honestly-flagged
 * gap — see the findings file — not a silent omission.
 */
@Configuration
class RecordPaymentResultConfiguration : SliceModule {

    override fun configure(configurer: EventSourcingConfigurer): EventSourcingConfigurer {
        val stateEntity = EventSourcedEntityModule.autodetected(
            UUID::class.java,
            RecordPaymentResultCommandHandler.PaymentState::class.java,
        )

        val commandHandlingModule = CommandHandlingModule
            .named("RecordPaymentResult")
            .commandHandlers()
            .autodetectedCommandHandlingComponent { RecordPaymentResultCommandHandler() }

        return configurer
            .registerEntity(stateEntity)
            .registerCommandHandlingModule(commandHandlingModule)
    }
}
