package com.dndmaster.adventure.infrastructure.integration;

import com.dndmaster.adventure.application.runtime.GmAgentPort;
import com.dndmaster.adventure.application.runtime.GmContextEnvelope;
import com.dndmaster.adventure.application.runtime.GmPlanResult;
import com.dndmaster.adventure.application.runtime.GmToolSpec;
import com.dndmaster.adventure.application.runtime.CombatEnemyProposal;
import com.dndmaster.adventure.application.runtime.CombatStartMode;
import com.dndmaster.adventure.application.runtime.RuntimePlan;
import com.dndmaster.adventure.application.runtime.SituationProposal;
import com.dndmaster.adventure.application.runtime.SituationUpdateProposal;
import com.dndmaster.adventure.domain.runtime.EffectiveGmProviderSelection;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** HTTP adapter for the role-specific Runtime GM contract. */
public final class HttpTypedRuntimeGmAgentPort implements GmAgentPort {
    private final HttpClient client;
    private final URI baseUri;
    private final Duration timeout;
    private final ObjectMapper mapper;
    private final String internalToken;

    public HttpTypedRuntimeGmAgentPort(HttpClient client, URI baseUri, Duration timeout,
            ObjectMapper mapper, String internalToken) {
        this.client = Objects.requireNonNull(client, "http client must not be null");
        this.baseUri = Objects.requireNonNull(baseUri, "base uri must not be null");
        this.timeout = Objects.requireNonNull(timeout, "timeout must not be null");
        this.mapper = Objects.requireNonNull(mapper, "object mapper must not be null");
        this.internalToken = Objects.requireNonNull(internalToken, "internal token must not be null");
    }

    @Override
    public GmPlanResult plan(GmContextEnvelope context) {
        try {
            RuntimeResponse response = call(context);
            String provider = context.provider().isBlank() ? "LEGACY_UNKNOWN" : context.provider();
            String model = context.model().isBlank() ? "LEGACY_UNKNOWN" : context.model();
            String reasoning = context.reasoning().isBlank() ? "LEGACY_UNKNOWN" : context.reasoning();
            EffectiveGmProviderSelection effective = context.requestedSelection().endpointId() == null
                    ? EffectiveGmProviderSelection.legacyUnknown()
                    : new EffectiveGmProviderSelection(context.requestedSelection().endpointId(), Instant.now(),
                            provider, model, reasoning);
            RuntimePlan plan = new RuntimePlan(response.scene(), context.currentContext().npcState(), response.judgment(),
                    response.narration(), null, context.evidencePack().storybook(), List.of(), provider, model,
                    reasoning, false, "", context.requestedSelection(), effective, 1, List.of(), null,
                    response.combatEnemies().stream().map(enemy -> new CombatEnemyProposal(
                            enemy.scenarioId(), enemy.enemyKey(), enemy.name(), enemy.count(),
                            combatStartMode(enemy.mode()))).toList(),
                    response.combatStart(), response.mapEntryRequested());
            return new GmPlanResult(plan, provider, model, reasoning, List.of(), List.of(), situation(response.situation()));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("typed runtime GM interrupted", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("typed runtime GM call failed: " + exception.getMessage(), exception);
        }
    }

    @Override
    public GmPlanResult plan(GmContextEnvelope context, List<GmToolSpec> ignoredTools) {
        return plan(context);
    }

    @Override
    public GmPlanResult plan(GmContextEnvelope context,
            com.dndmaster.adventure.application.runtime.TurnCapability ignoredCapability,
            List<GmToolSpec> ignoredTools) {
        return plan(context);
    }

    private RuntimeResponse call(GmContextEnvelope context) throws Exception {
        Map<String, Object> runtimeContext = new HashMap<>();
        runtimeContext.put("currentContext", context.currentContext());
        runtimeContext.put("scenarioContext", context.scenarioContext());
        runtimeContext.put("recentTurns", context.recentTurns());
        runtimeContext.put("characterSnapshots", context.characterSnapshots());
        runtimeContext.put("narrativeContext", context.narrativeContext());
        String body = mapper.writeValueAsString(new RuntimeRequest(context.operationKey(), context.action(),
                compositeResults(context), Map.copyOf(runtimeContext)));
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("internal/gm/runtime-turn"))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("X-Internal-Token", internalToken)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("typed runtime GM returned " + response.statusCode() + ": " + response.body());
        }
        RuntimeResponse result = mapper.readValue(response.body(), RuntimeResponse.class);
        if (result.scene() == null || result.scene().isBlank()
                || result.judgment() == null || result.judgment().isBlank()
                || result.narration() == null || result.narration().isBlank() || result.situation() == null) {
            throw new IllegalStateException("typed runtime GM response is incomplete");
        }
        if (result.combatEnemies() == null || (result.combatStart() && result.combatEnemies().isEmpty())) {
            throw new IllegalStateException("typed runtime GM combat response is incomplete");
        }
        return result;
    }

    private static SituationProposal situation(SituationResponse response) {
        SituationUpdateProposal update = "TRANSITION".equalsIgnoreCase(response.kind())
                ? SituationUpdateProposal.transition(response.location(), response.problem(), response.threat(), response.goal())
                : SituationUpdateProposal.continueSituation(response.problem(), response.threat(), response.goal());
        SituationProposal.Basis basis = SituationProposal.Basis.valueOf(response.basis().trim().toUpperCase(java.util.Locale.ROOT));
        return new SituationProposal(update, basis, response.reference(), response.required());
    }

    private static CombatStartMode combatStartMode(String mode) {
        return switch (mode == null ? "" : mode.toUpperCase(java.util.Locale.ROOT)) {
            case "INSTANT" -> CombatStartMode.INSTANT;
            case "SITUATION" -> CombatStartMode.SITUATION;
            default -> CombatStartMode.SCENARIO;
        };
    }

    private static List<Map<String, Object>> compositeResults(GmContextEnvelope context) {
        List<Map<String, Object>> results = new ArrayList<>();
        context.evidencePack().storybook().forEach(evidence -> {
            Map<String, Object> result = new HashMap<>();
            result.put("source", "STORYBOOK_RAG");
            result.put("answer", evidence.excerpt());
            result.put("locator", evidence.locator());
            if (evidence.citationKey() != null) result.put("citationKey", evidence.citationKey());
            results.add(Map.copyOf(result));
        });
        return List.copyOf(results);
    }

    record RuntimeRequest(String operationKey, String action, List<Map<String, Object>> factLookupResults,
                          Map<String, Object> runtimeContext) { }
    record RuntimeResponse(String scene, String judgment, String narration, boolean combatStart,
                           List<CombatEnemyResponse> combatEnemies, SituationResponse situation,
                           boolean mapEntryRequested) { }
    record CombatEnemyResponse(String mode, String scenarioId, String enemyKey, String name, int count) { }
    record SituationResponse(String kind, String location, String problem, String threat, String goal,
                             String basis, String reference, boolean required) { }
}
