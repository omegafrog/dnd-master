package com.dndmaster.combatmap.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ApiContractExceptionHandlerTest {
    @Test
    void maps_invalid_internal_token_to_unauthorized_response() {
        var response = new ApiContractExceptionHandler().handle(
                new ApiRequestGuard.ApiContractException(401, "INVALID_SERVICE_TOKEN"));

        assertEquals(401, response.getStatusCode().value());
        assertEquals("INVALID_SERVICE_TOKEN", response.getBody().code());
    }
}
