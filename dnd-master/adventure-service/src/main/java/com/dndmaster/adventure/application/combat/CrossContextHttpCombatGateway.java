package com.dndmaster.adventure.application.combat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;

public final class CrossContextHttpCombatGateway
        implements CharacterCombatPort, DiceCombatPort, CombatMapPort, AiCombatPort {
    private final HttpClient client;
    private final URI baseUri;
    private final Duration timeout;

    public CrossContextHttpCombatGateway(HttpClient client, URI baseUri, Duration timeout) {
        this.client = Objects.requireNonNull(client);
        this.baseUri = Objects.requireNonNull(baseUri);
        this.timeout = Objects.requireNonNull(timeout);
    }

    @Override
    public void requireUsableCharacter(CombatActionCommand command) {
        send("characters/" + command.characterSheetId().value() + "?adventureId=" + command.adventureId().value(),
                "GET", "", command);
    }

    @Override
    public int roll(CombatActionCommand command) {
        String value = send("rolls", "POST", command.role().name(), command);
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            throw new CrossContextCallException("Dice Roll BC returned malformed total", exception);
        }
    }

    @Override
    public void validateAndMove(CombatActionCommand command) {
        send("moves", "POST", command.movementPath(), command);
    }

    @Override
    public void controlState(CombatActionCommand command) {
        send("ai/states", "POST", command.role() + ":" + command.action(), command);
    }

    @Override
    public String adjudicate(CombatActionCommand command, int diceTotal) {
        return send("ai/adjudications", "POST", command.ruleSetId().value() + ":" + diceTotal, command).trim();
    }

    private String send(String path, String method, String body, CombatActionCommand command) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(baseUri.resolve(path))
                .timeout(timeout)
                .header("Idempotency-Key", command.operationId().toString());
        if (method.equals("GET")) builder.GET();
        else builder.POST(HttpRequest.BodyPublishers.ofString(body));
        try {
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new CrossContextCallException("cross-context call failed with status " + response.statusCode());
            }
            return response.body();
        } catch (IOException exception) {
            throw new CrossContextCallException("cross-context transport failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CrossContextCallException("cross-context call interrupted", exception);
        }
    }
}
