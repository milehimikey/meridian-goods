package com.meridiangoods.openorders

import com.meridiangoods.axon.SliceModule
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer
import org.axonframework.messaging.eventhandling.configuration.EventProcessorModule
import org.axonframework.messaging.queryhandling.configuration.QueryHandlingModule
import org.springframework.context.annotation.Configuration

/**
 * Slice wiring for Open Orders. This is the *only* file that changes to bring the slice
 * online — it registers itself as a [SliceModule] Spring bean, discovered by
 * [com.meridiangoods.axon.AxonApplicationConfiguration]'s component scan. No other file in the
 * project needs to be edited to add this slice.
 */
@Configuration
class OpenOrdersConfiguration : SliceModule {

    override fun configure(configurer: EventSourcingConfigurer): EventSourcingConfigurer {
        val projectionProcessor = EventProcessorModule
            .pooledStreaming("Projection_OpenOrders_Processor")
            .eventHandlingComponents { c ->
                c.autodetected { cfg -> OpenOrdersProjection(cfg.getComponent(OpenOrdersRepository::class.java)) }
            }
            .notCustomized()

        val queryHandlingModule = QueryHandlingModule
            .named("get-open-orders")
            .queryHandlers()
            .autodetectedQueryHandlingComponent { cfg ->
                GetOpenOrdersQueryHandler(cfg.getComponent(OpenOrdersRepository::class.java))
            }

        return configurer
            .componentRegistry { cr ->
                cr.registerComponent(OpenOrdersRepository::class.java) { InMemoryOpenOrdersRepository() }
            }
            .registerQueryHandlingModule(queryHandlingModule)
            .modelling { m ->
                m.messaging { ms ->
                    ms.eventProcessing { ep -> ep.pooledStreaming { ps -> ps.processor(projectionProcessor) } }
                }
            }
    }
}
