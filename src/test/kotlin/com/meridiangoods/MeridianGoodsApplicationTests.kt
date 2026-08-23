package com.meridiangoods

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.axonframework.messaging.commandhandling.gateway.CommandGateway
import org.axonframework.messaging.eventhandling.gateway.EventGateway
import kotlin.test.assertNotNull

/**
 * Proves the scaffold's Spring + Axon wiring boots end to end with zero slices registered and
 * zero external services (no Axon Server, no database, no Testcontainers) — the whole point of
 * [com.meridiangoods.axon.AxonApplicationConfiguration] folding an empty `List<SliceModule>`.
 */
@SpringBootTest
class MeridianGoodsApplicationTests {

    @Autowired
    private lateinit var commandGateway: CommandGateway

    @Autowired
    private lateinit var eventGateway: EventGateway

    @Test
    fun `application context loads and exposes Axon gateways with no slices registered`() {
        assertNotNull(commandGateway)
        assertNotNull(eventGateway)
    }
}
