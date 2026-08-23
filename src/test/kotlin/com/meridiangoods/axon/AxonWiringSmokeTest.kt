package com.meridiangoods.axon

import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer
import org.axonframework.test.fixture.AxonTestFixture
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * The non-Spring-Boot half of the "AxonTestFixture buildable per slice" story: building a fixture
 * directly from a plain [EventSourcingConfigurer] (no Spring context, no external services) is
 * how every future slice's own fixture test will start — this proves the mechanism works before
 * any slice exists. See `axon-entity-testing` / `axon-testing-setup` for the pattern each slice's
 * own test class follows.
 */
class AxonWiringSmokeTest {

    private lateinit var fixture: AxonTestFixture

    @BeforeEach
    fun beforeEach() {
        fixture = AxonTestFixture.with(EventSourcingConfigurer.create())
    }

    @AfterEach
    fun afterEach() {
        fixture.stop()
    }

    @Test
    fun `fixture starts against an empty configurer with no external services`() {
        fixture.given()
            .`when`()
            .nothing()
            .then()
            .noEvents()
    }
}
