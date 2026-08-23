package com.meridiangoods.axon

import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer

/**
 * One Spring bean per vertical slice.
 *
 * Every slice under `com.meridiangoods.<slicekey>` ships exactly one `@Configuration` class that
 * implements this interface and registers *that slice's own* entities / command handling
 * modules / query handling modules onto the shared [EventSourcingConfigurer]. Because the slice
 * declares itself as a normal Spring bean, [AxonApplicationConfiguration] discovers it via
 * `List<SliceModule>` constructor injection — Spring's own component scan does the registration,
 * not a hand-maintained list.
 *
 * This is the mechanism that makes slice wiring conflict-free by construction: adding a new
 * slice means adding a new package with a new `@Configuration` class in it. No existing file
 * (this one included) is ever edited to "wire in" a slice, so two slices implemented in parallel
 * branches never touch the same line of infrastructure code.
 */
fun interface SliceModule {
    fun configure(configurer: EventSourcingConfigurer): EventSourcingConfigurer
}
