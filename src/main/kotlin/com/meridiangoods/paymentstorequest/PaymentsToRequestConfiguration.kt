package com.meridiangoods.paymentstorequest

import com.meridiangoods.axon.SliceModule
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer
import org.axonframework.messaging.eventhandling.configuration.EventProcessorModule
import org.axonframework.messaging.queryhandling.configuration.QueryHandlingModule
import org.springframework.context.annotation.Configuration

/**
 * Slice wiring for Payments To Request. This is the *only* file that changes to bring the slice
 * online — it registers itself as a [SliceModule] Spring bean, discovered by
 * [com.meridiangoods.axon.AxonApplicationConfiguration]'s component scan. No other file in the
 * project needs to be edited to add this slice (see `w3-1-scaffold-place-order.md` for why).
 */
@Configuration
class PaymentsToRequestConfiguration : SliceModule {

    override fun configure(configurer: EventSourcingConfigurer): EventSourcingConfigurer {
        val projectionProcessor = EventProcessorModule
            .pooledStreaming("Projection_PaymentsToRequest_Processor")
            .eventHandlingComponents { c ->
                c.autodetected { cfg ->
                    PaymentsToRequestProjection(cfg.getComponent(PaymentsToRequestRepository::class.java))
                }
            }
            .notCustomized()

        val queryHandlingModule = QueryHandlingModule
            .named("list-payments-to-request")
            .queryHandlers()
            .autodetectedQueryHandlingComponent { cfg ->
                ListPaymentsToRequestQueryHandler(cfg.getComponent(PaymentsToRequestRepository::class.java))
            }

        return configurer
            .componentRegistry { cr ->
                cr.registerComponent(PaymentsToRequestRepository::class.java) { InMemoryPaymentsToRequestRepository() }
            }
            .registerQueryHandlingModule(queryHandlingModule)
            .modelling { m ->
                m.messaging { ms ->
                    ms.eventProcessing { ep -> ep.pooledStreaming { ps -> ps.processor(projectionProcessor) } }
                }
            }
    }
}
