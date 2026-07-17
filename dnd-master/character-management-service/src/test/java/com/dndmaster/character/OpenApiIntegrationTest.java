package com.dndmaster.character;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OpenApiIntegrationTest {
    @LocalServerPort int port;
    @Autowired TestRestTemplate http;

    @Test void exposesSwaggerOpenApiAndHealth() {
        assertThat(http.getForEntity(url("/swagger-ui/index.html"), String.class).getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(http.getForObject(url("/v3/api-docs"), String.class)).contains("\"openapi\"");
        assertThat(http.getForObject(url("/actuator/health"), String.class)).contains("\"status\":\"UP\"");
    }

    private String url(String path) { return "http://127.0.0.1:" + port + path; }
}
