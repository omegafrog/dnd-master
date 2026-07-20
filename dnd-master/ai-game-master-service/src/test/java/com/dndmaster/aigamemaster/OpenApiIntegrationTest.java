package com.dndmaster.aigamemaster;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIf("isOllamaAvailable")
class OpenApiIntegrationTest {
    @LocalServerPort int port;
    @Autowired TestRestTemplate http;

    @Test void exposesSwaggerOpenApiAndHealth() {
        assertThat(http.getForEntity(url("/swagger-ui/index.html"), String.class).getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(http.getForObject(url("/v3/api-docs"), String.class)).contains("\"openapi\"");
        assertThat(http.getForObject(url("/actuator/health"), String.class)).contains("\"status\":\"UP\"");
    }

    private String url(String path) { return "http://127.0.0.1:" + port + path; }

    static boolean isOllamaAvailable() {
        try {
            var client = java.net.http.HttpClient.newHttpClient();
            var request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create("http://localhost:11434/api/tags"))
                    .timeout(java.time.Duration.ofSeconds(2))
                    .GET()
                    .build();
            var response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }
}
