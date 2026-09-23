package com.dndmaster.adventure.infrastructure.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.adventure.application.runtime.RuntimeCharacterSheetReadException;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class HttpRuntimeCharacterSheetReadPortTest {
    private WireMockServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop();
    }

    @Test
    void reads_the_current_sheet_from_the_existing_runtime_endpoint() {
        server = new WireMockServer(0);
        server.start();
        UUID sheetId = UUID.randomUUID();
        String sheet = "{\"characterSheetId\":\"" + sheetId + "\",\"characterName\":\"민아\",\"characterState\":\"{\\\"currentHitPoints\\\":7}\"}";
        server.stubFor(get(urlEqualTo("/internal/v1/character-sheets/" + sheetId + "/runtime"))
                .willReturn(aResponse().withStatus(200).withBody(sheet)));
        HttpRuntimeCharacterSheetReadPort port = port();

        assertEquals(sheet, port.read(sheetId));
        server.verify(getRequestedFor(urlEqualTo("/internal/v1/character-sheets/" + sheetId + "/runtime")));
    }

    @Test
    void rejects_a_failed_or_incomplete_read_before_a_gm_call() {
        server = new WireMockServer(0);
        server.start();
        UUID sheetId = UUID.randomUUID();
        server.stubFor(get(urlEqualTo("/internal/v1/character-sheets/" + sheetId + "/runtime"))
                .willReturn(aResponse().withStatus(503)));

        assertThrows(RuntimeCharacterSheetReadException.class, () -> port().read(sheetId));
    }

    private HttpRuntimeCharacterSheetReadPort port() {
        return new HttpRuntimeCharacterSheetReadPort(HttpClient.newHttpClient(), URI.create(server.baseUrl() + "/"),
                Duration.ofSeconds(2), "service-token");
    }
}
