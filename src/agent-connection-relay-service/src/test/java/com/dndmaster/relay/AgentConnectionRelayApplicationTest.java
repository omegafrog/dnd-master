package com.dndmaster.relay;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {"INTERNAL_SERVICE_TOKEN=test-token", "relay.instance-id=test-relay"})
class AgentConnectionRelayApplicationTest {
    @Test void applicationContextWiresWebFluxNettyRelay() {}
}
