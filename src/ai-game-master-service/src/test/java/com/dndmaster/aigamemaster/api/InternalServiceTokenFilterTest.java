package com.dndmaster.aigamemaster.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class InternalServiceTokenFilterTest {
    @Test
    void rejectsMissingAndWrongTokensForEveryInternalPath() throws Exception {
        var filter = new InternalServiceTokenFilter("service-secret");
        for (String token : new String[] {null, "wrong"}) {
            var request = new MockHttpServletRequest("POST", "/internal/v1/gm/scenes");
            if (token != null) request.addHeader("X-Internal-Token", token);
            var response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
            assertEquals(401, response.getStatus());
        }
    }

    @Test
    void acceptsTheConfiguredTokenAndDoesNotGuardPublicRoutes() throws Exception {
        var filter = new InternalServiceTokenFilter("service-secret");
        var internal = new MockHttpServletRequest("POST", "/internal/gm/runtime-turn");
        internal.addHeader("X-Internal-Token", "service-secret");
        var chain = new MockFilterChain();
        filter.doFilter(internal, new MockHttpServletResponse(), chain);
        org.junit.jupiter.api.Assertions.assertNotNull(chain.getRequest());

        var publicRequest = new MockHttpServletRequest("GET", "/api/v1/profile/agent-endpoints");
        var publicChain = new MockFilterChain();
        filter.doFilter(publicRequest, new MockHttpServletResponse(), publicChain);
        org.junit.jupiter.api.Assertions.assertNotNull(publicChain.getRequest());

        var otherInternal = new MockHttpServletRequest("POST", "/internal/v1/auth/introspections");
        var otherChain = new MockFilterChain();
        filter.doFilter(otherInternal, new MockHttpServletResponse(), otherChain);
        org.junit.jupiter.api.Assertions.assertNotNull(otherChain.getRequest());
    }

    @Test
    void failsFastWhenTheServiceTokenIsMissing() {
        assertThrows(ApiRequestGuard.ApiContractException.class, () -> new InternalServiceTokenFilter(" "));
    }
}
