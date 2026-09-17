package com.dndmaster.combatmap.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dndmaster.combatmap.application.spatial.SpatialPreparationCommandConflictException;
import com.dndmaster.combatmap.application.spatial.SpatialPreparationVersionConflictException;
import org.junit.jupiter.api.Test;

class ApiContractExceptionHandlerTest {
    @Test
    void maps_invalid_internal_token_to_unauthorized_response() {
        var response = new ApiContractExceptionHandler().handle(
                new ApiRequestGuard.ApiContractException(401, "INVALID_SERVICE_TOKEN"));

        assertEquals(401, response.getStatusCode().value());
        assertEquals("INVALID_SERVICE_TOKEN", response.getBody().code());
    }

    @Test
    void maps_spatial_preparation_retries_to_typed_conflicts() {
        var handler = new ApiContractExceptionHandler();

        var command = handler.handle(new SpatialPreparationCommandConflictException());
        var version = handler.handle(new SpatialPreparationVersionConflictException());

        assertEquals(409, command.getStatusCode().value());
        assertEquals("SPATIAL_PREPARATION_COMMAND_CONFLICT", command.getBody().code());
        assertEquals(409, version.getStatusCode().value());
        assertEquals("SPATIAL_PREPARATION_VERSION_CONFLICT", version.getBody().code());
    }
}
