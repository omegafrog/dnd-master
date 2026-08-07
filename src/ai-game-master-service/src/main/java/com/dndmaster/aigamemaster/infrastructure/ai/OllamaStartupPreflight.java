package com.dndmaster.aigamemaster.infrastructure.ai;

import com.dndmaster.aigamemaster.configuration.LocalOllamaProperties;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;

@Component
@Profile("!retrieval-evaluation")
public final class OllamaStartupPreflight implements ApplicationRunner {
    private final LocalOllamaProperties properties;
    private final OllamaModelInventory inventory;

    @Autowired
    public OllamaStartupPreflight(LocalOllamaProperties properties) {
        this(properties, new HttpOllamaModelInventory());
    }

    OllamaStartupPreflight(LocalOllamaProperties properties, OllamaModelInventory inventory) {
        this.properties = properties;
        this.inventory = inventory;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        verify();
    }

    public void verify() {
        properties.validate();
        Set<String> installedModels = inventory.installedModels(properties.baseUrl(), properties.requestTimeout());
        if (!installedModels.contains(properties.chatModel()) || !installedModels.contains(properties.embeddingModel())) {
            throw new IllegalStateException("Required Ollama model is not installed");
        }
    }

    interface OllamaModelInventory {
        Set<String> installedModels(URI baseUrl, Duration timeout);
    }

    static final class HttpOllamaModelInventory implements OllamaModelInventory {
        private final HttpClient client = HttpClient.newHttpClient();

        @Override
        public Set<String> installedModels(URI baseUrl, Duration timeout) {
            try {
                HttpRequest request = HttpRequest.newBuilder(baseUrl.resolve("/api/tags")).timeout(timeout).GET().build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    throw new IllegalStateException("Ollama runtime is unavailable");
                }
                return OllamaModelNames.parse(response.body());
            } catch (IOException e) {
                throw new IllegalStateException("Ollama runtime is unavailable", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Ollama runtime probe interrupted", e);
            }
        }
    }
}
