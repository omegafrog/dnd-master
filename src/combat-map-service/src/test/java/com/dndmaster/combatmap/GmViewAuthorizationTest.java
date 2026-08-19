package com.dndmaster.combatmap;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.combatmap.api.ApiRequestGuard;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GmViewAuthorizationTest {
    @Test
    void rejectsUnauthenticatedAndWrongServiceRequestsButAllowsTheConfiguredInternalService() {
        var guard = new ApiRequestGuard("service-secret");
        var owner = UUID.randomUUID();

        assertThrows(ApiRequestGuard.ApiContractException.class, () -> guard.internal(null));
        assertThrows(ApiRequestGuard.ApiContractException.class, () -> guard.internal("wrong-service"));
        assertDoesNotThrow(() -> guard.internal("service-secret"));
        assertDoesNotThrow(() -> guard.publicOwner("Bearer player", owner, owner));
    }
}
