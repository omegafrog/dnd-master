package com.dndmaster.adventure;

import static org.assertj.core.api.Assertions.assertThat;

import com.dndmaster.adventure.AdventureServiceApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;

import javax.sql.DataSource;

@SpringBootTest(
        classes = AdventureServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.main.lazy-initialization=true",
            "spring.flyway.enabled=false",
            "management.health.db.enabled=false",
            "INTERNAL_SERVICE_TOKEN=test-internal-token"
        })
class OpenApiIntegrationTest {
    @LocalServerPort int port;
    @Autowired TestRestTemplate http;
    @MockBean DataSource dataSource;

    @Test void exposesSwaggerOpenApiAndHealth() {
        assertThat(http.getForEntity(url("/swagger-ui/index.html"), String.class).getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(http.getForObject(url("/v3/api-docs"), String.class)).contains("\"openapi\"");
        assertThat(http.getForObject(url("/actuator/health"), String.class)).contains("\"status\":\"UP\"");
    }

    private String url(String path) { return "http://127.0.0.1:" + port + path; }
}
