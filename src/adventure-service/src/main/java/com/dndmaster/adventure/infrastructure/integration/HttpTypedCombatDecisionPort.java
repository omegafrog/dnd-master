package com.dndmaster.adventure.infrastructure.integration;

import com.dndmaster.adventure.application.combat.AiCombatDecisionPort;
import com.dndmaster.adventure.application.combat.AiCombatTurnContext;
import com.dndmaster.adventure.application.combat.AiTurnPlan;
import com.dndmaster.adventure.application.combat.AiCombatDecisionPortAdapter;
import com.dndmaster.adventure.domain.combat.CombatActionIntent;
import com.dndmaster.adventure.domain.combat.FreeFormActionPlan;
import com.dndmaster.adventure.domain.combat.TurnResourceCost;
import com.dndmaster.adventure.domain.runtime.RequestedGmProviderSelection;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Calls the typed AI Game Master combat-decision contract; returned proposals remain untrusted. */
public final class HttpTypedCombatDecisionPort implements AiCombatDecisionPort {
    private final HttpClient client;
    private final URI baseUri;
    private final Duration timeout;
    private final ObjectMapper mapper;
    private final String internalToken;
    private final AiCombatDecisionPort legacyFreeForm;

    public HttpTypedCombatDecisionPort(HttpClient client, URI baseUri, Duration timeout,
            ObjectMapper mapper, String internalToken, AiCombatDecisionPort legacyFreeForm) {
        this.client = Objects.requireNonNull(client);
        this.baseUri = Objects.requireNonNull(baseUri);
        this.timeout = Objects.requireNonNull(timeout);
        this.mapper = Objects.requireNonNull(mapper);
        this.internalToken = Objects.requireNonNull(internalToken);
        this.legacyFreeForm = Objects.requireNonNull(legacyFreeForm);
    }

    @Override public FreeFormActionPlan interpretFreeForm(com.dndmaster.adventure.application.combat.FreeFormCombatContext context) {
        return legacyFreeForm.interpretFreeForm(context);
    }

    @Override public AiTurnPlan planTurn(AiCombatTurnContext context) {
        try {
            UUID owner = Objects.requireNonNull(context.ownerPlayerId(), "combat owner is required");
            var selection = context.providerSelection();
            if (selection == null) throw new IllegalStateException("combat GM provider selection is unavailable");
            RequestedGmProviderSelection requested = new RequestedGmProviderSelection(selection.endpointId(),
                    selection.provider(), selection.model(), selection.reasoning());
            var selectedRequest = new HttpPayloads.RuntimeEndpointRequest(requested.endpointId(), requested.provider(),
                    requested.model(), requested.reasoning());
            HttpResponse<String> selected = post("internal/gm/runtime-endpoint", selectedRequest);
            if (selected.statusCode() / 100 != 2) throw new IllegalStateException("combat GM endpoint selection failed");
            RuntimeEndpointResponse effective = mapper.readValue(selected.body(), RuntimeEndpointResponse.class);
            String operationKey = "combat-turn:" + context.encounter().encounterId() + ":"
                    + context.encounter().version() + ":" + context.actor().participantId();
            String prompt = prompt(context);
            var request = new CombatTurnDecisionRequest(owner, operationKey, requested.endpointId(), requested.provider(),
                    requested.model(), requested.reasoning(), effective.endpointId(), effective.endpointVersion(),
                    effective.provider(), effective.model(), prompt);
            HttpResponse<String> response = post("internal/gm/combat-turn-decision", request);
            if (response.statusCode() / 100 != 2) throw new IllegalStateException("combat decision provider returned " + response.statusCode());
            CombatTurnDecisionResponse decision = mapper.readValue(response.body(), CombatTurnDecisionResponse.class);
            if (decision.citationKeys() == null || decision.citationKeys().isEmpty()) {
                throw new IllegalArgumentException("combat decision must cite its rules");
            }
            if ("END_TURN".equals(decision.kind())) {
                return AiTurnPlan.endTurn(decision.actorId(), decision.endTurnAssessment(), decision.citationKeys());
            }
            if (!"ACTION".equals(decision.kind())) throw new IllegalArgumentException("combat decision kind is invalid");
            return new AiTurnPlan(decision.actorId(), new CombatActionIntent(decision.actorId(), decision.action(),
                    TurnResourceCost.actionOnly()), null, null, decision.targetId(), null, false, null,
                    decision.citationKeys(), null);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("combat decision request was interrupted", interrupted);
        } catch (Exception failure) {
            if (failure instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("combat decision request failed", failure);
        }
    }

    private HttpResponse<String> post(String path, Object body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve(path)).timeout(timeout)
                .header("Content-Type", "application/json").header("X-Internal-Token", internalToken)
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String prompt(AiCombatTurnContext context) throws Exception {
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("encounterId", context.encounter().encounterId());
        data.put("encounterVersion", context.encounter().version());
        data.put("round", context.encounter().round());
        data.put("participants", context.encounter().participants());
        data.put("actor", context.actor());
        data.put("currentSituation", context.currentSituation());
        data.put("characterSheet", context.characterSheetJson());
        data.put("enemyCharacterSheet", context.enemyCharacterSheet());
        data.put("rulebookEvidence", context.ruleEvidence());
        data.put("tacticalInstruction", context.tacticalInstruction());
        return "ROLE=COMBAT_TURN_DECISION\n" +
                "Choose one legal action that benefits this actor in the current situation. Use supplied character sheet and enemy action rules first; use the supplied pinned Rulebook excerpts only when needed rules are absent from that material. Never invent rule values. " +
                "Return one JSON object with every field: kind, actorId, action, targetId, citationKeys, endTurnAssessment. " +
                "citationKeys is REQUIRED for both ACTION and END_TURN and MUST be a non-empty JSON array of exact citation keys copied from the supplied character sheet, enemy character sheet, or rulebookEvidence. Never omit this field, return an empty array, cite a title in prose, or invent a key. " +
                "For ACTION, include the exact source key or keys that support the chosen action. For END_TURN, cite every reviewed action rule in the same array. " +
                "For a companion, action must be exactly one Runtime command name: ATTACK, CAST_SPELL, DODGE, DISENGAGE, DASH, HELP, HIDE, READY, SEARCH, USE_OBJECT, or MOVE. " +
                "For an enemy, action must exactly match an action name on enemyCharacterSheet.actions. Do not return a descriptive phrase or a spell/action name in place of the Runtime command name. " +
                "For END_TURN, explain why no favorable legal action is available and cite every relevant reviewed rule. " +
                "For ACTION, cite the supplied rule keys supporting the action. Do not include narration or hidden values.\nCONTEXT="
                + mapper.writeValueAsString(data);
    }

    private record RuntimeEndpointResponse(UUID endpointId, String endpointVersion,
            String provider, String model, String reasoning) { }
    private record CombatTurnDecisionRequest(UUID soloPlayerId, String operationKey,
            UUID endpointId, String provider, String model, String reasoning,
            UUID effectiveEndpointId, String effectiveEndpointVersion,
            String effectiveProvider, String effectiveModel, String prompt) { }
    private record CombatTurnDecisionResponse(UUID actorId, String kind, String action,
            UUID targetId, List<String> citationKeys, String endTurnAssessment) { }
    private static final class HttpPayloads {
        private record RuntimeEndpointRequest(UUID endpointId, String provider, String model, String reasoning) { }
    }
}
