package com.meridiangoods.orderstatus

import com.meridiangoods.axon.SliceModule
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer
import org.axonframework.messaging.eventhandling.configuration.EventProcessorModule
import org.axonframework.messaging.queryhandling.configuration.QueryHandlingModule
import org.springframework.context.annotation.Configuration

/**
 * Slice wiring for Order Status. This is the *only* file that changes to bring the slice
 * online — it registers itself as a [SliceModule] Spring bean, discovered by
 * [com.meridiangoods.axon.AxonApplicationConfiguration]'s component scan. No other file in the
 * project needs to be edited to add this slice.
 */
@Configuration
class OrderStatusConfiguration : SliceModule {

    override fun configure(configurer: EventSourcingConfigurer): EventSourcingConfigurer {
        val projectionProcessor = EventProcessorModule
            .pooledStreaming("Projection_OrderStatus_Processor")
            .eventHandlingComponents { c ->
                c.autodetected { cfg -> OrderStatusProjection(cfg.getComponent(OrderStatusRepository::class.java)) }
            }
            .notCustomized()

        val queryHandlingModule = QueryHandlingModule
            .named("get-order-status")
            .queryHandlers()
            .autodetectedQueryHandlingComponent { cfg ->
                GetOrderStatusQueryHandler(cfg.getComponent(OrderStatusRepository::class.java))
            }

        return configurer
            .componentRegistry { cr ->
                cr.registerComponent(OrderStatusRepository::class.java) { InMemoryOrderStatusRepository() }
            }
            .registerQueryHandlingModule(queryHandlingModule)
            .modelling { m ->
                m.messaging { ms ->
                    ms.eventProcessing { ep -> ep.pooledStreaming { ps -> ps.processor(projectionProcessor) } }
                }
            }
    }
}
