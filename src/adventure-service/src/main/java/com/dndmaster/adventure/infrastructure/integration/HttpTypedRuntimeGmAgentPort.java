package com.dndmaster.adventure.infrastructure.integration;

import com.dndmaster.adventure.application.runtime.GmAgentPort;
import com.dndmaster.adventure.application.runtime.GmContextEnvelope;
import com.dndmaster.adventure.application.runtime.RuntimeGmContextLimits;
import com.dndmaster.adventure.application.runtime.RuntimeGmInputLimitException;
import com.dndmaster.adventure.application.runtime.GmPlanResult;
import com.dndmaster.adventure.application.runtime.CompletionCandidate;
import com.dndmaster.adventure.application.runtime.GmToolSpec;
import com.dndmaster.adventure.application.runtime.CombatEnemyProposal;
import com.dndmaster.adventure.application.runtime.CombatStartMode;
import com.dndmaster.adventure.application.runtime.RuntimePlan;
import com.dndmaster.adventure.application.runtime.SituationProposal;
import com.dndmaster.adventure.application.runtime.SituationUpdateProposal;
import com.dndmaster.adventure.domain.runtime.EffectiveGmProviderSelection;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** HTTP adapter for the role-specific Runtime GM contract. */
public final class HttpTypedRuntimeGmAgentPort implements GmAgentPort {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(HttpTypedRuntimeGmAgentPort.class);
    private final HttpClient client;
    private final URI baseUri;
    private final Duration timeout;
    private final ObjectMapper mapper;
    private final String internalToken;
    private final RuntimeGmContextLimits contextLimits;

    public HttpTypedRuntimeGmAgentPort(HttpClient client, URI baseUri, Duration timeout,
            ObjectMapper mapper, String internalToken) {
        this(client, baseUri, timeout, mapper, internalToken, "");
    }

    public HttpTypedRuntimeGmAgentPort(HttpClient client, URI baseUri, Duration timeout,
            ObjectMapper mapper, String internalToken, String configuredLimits) {
        this.contextLimits = new RuntimeGmContextLimits(configuredLimits);
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
            Map<String, com.dndmaster.adventure.application.runtime.RuntimeEvidence> evidenceByKey = context.evidencePack().all().stream()
                    .collect(java.util.stream.Collectors.toMap(
                            com.dndmaster.adventure.application.runtime.RuntimeEvidence::referenceKey,
                            evidence -> evidence, (first, ignored) -> first));
            List<String> unknownCitationKeys = response.citedEvidence().stream()
                    .filter(key -> !evidenceByKey.containsKey(key)).distinct().toList();
            if (!unknownCitationKeys.isEmpty()) {
                LOGGER.warn("gm_runtime_unknown_citation_keys operationKey={} keys={}", context.turnId(), unknownCitationKeys);
            }
            List<com.dndmaster.adventure.application.runtime.RuntimeEvidence> citedEvidence = new java.util.ArrayList<>(response.citedEvidence().stream()
                    .map(evidenceByKey::get).filter(Objects::nonNull).distinct().toList());
            List<String> unknownCheckEvidenceKeys = response.checkProposal().evidenceKeys().stream()
                    .filter(key -> !evidenceByKey.containsKey(key)).distinct().toList();
            if (response.checkProposal().required() && !unknownCheckEvidenceKeys.isEmpty()) {
                throw new IllegalArgumentException("runtime check proposal cites evidence outside the supplied pack");
            }
            if (response.checkProposal().required()) {
                response.checkProposal().evidenceKeys().stream().map(evidenceByKey::get).filter(Objects::nonNull)
                        .forEach(citedEvidence::add);
                citedEvidence = citedEvidence.stream().distinct().toList();
            }
            var checkProposal = response.checkProposal().required()
                    ? new com.dndmaster.adventure.application.runtime.RuntimeCheckProposal(true,
                            response.checkProposal().reason(), response.checkProposal().abilityOrSkill(),
                            response.checkProposal().characterSheetId(),
                            response.checkProposal().rollMethod().equals("플레이어")
                                    ? com.dndmaster.adventure.application.runtime.RuntimeCheckProposal.RollMethod.PLAYER
                                    : com.dndmaster.adventure.application.runtime.RuntimeCheckProposal.RollMethod.SYSTEM,
                            response.checkProposal().diceExpression(), response.checkProposal().modifier(),
                            response.checkProposal().difficulty(), response.checkProposal().evidenceKeys(),
                            response.checkProposal().successOutcome(), response.checkProposal().failureOutcome())
                    : com.dndmaster.adventure.application.runtime.RuntimeCheckProposal.none();
            for (CombatEnemyResponse enemy : response.combatEnemies()) {
                java.util.stream.Stream.concat(enemy.abilities().stream().flatMap(a -> a.citationKeys().stream()),
                        enemy.actions().stream().flatMap(a -> a.citationKeys().stream()))
                        .filter(key -> !evidenceByKey.containsKey(key)).findFirst().ifPresent(key -> {
                            throw new IllegalArgumentException("enemy sheet candidate cites evidence outside the supplied pack");
                        });
            }
            RuntimePlan plan = new RuntimePlan(response.scene(), context.currentContext().npcState(), response.judgment(),
                    response.narration(), null, citedEvidence, List.of(), provider, model,
                    reasoning, false, "", context.requestedSelection(), effective, 1, List.of(), null,
                    response.combatEnemies().stream().map(enemy -> new CombatEnemyProposal(
                            enemy.scenarioId(), enemy.enemyKey(), enemy.name(), enemy.count(),
                            combatStartMode(enemy.mode())).withSheetCandidates(
                                    enemy.abilities().stream().map(a -> new com.dndmaster.adventure.application.runtime.CombatEnemyAbilityProposal(
                                            a.ability(), a.score(), a.citationKeys())).toList(),
                                    enemy.actions().stream().map(a -> new com.dndmaster.adventure.application.runtime.CombatEnemyActionProposal(
                                            a.name(), a.description(), a.citationKeys())).toList())).toList(),
                    response.combatStart(), response.mapEntryRequested(), checkProposal);
            List<com.dndmaster.adventure.application.runtime.RuntimeAddedFactCandidate> runtimeFacts = response.runtimeFacts().stream()
                    .map(fact -> new com.dndmaster.adventure.application.runtime.RuntimeAddedFactCandidate(fact.subject(), fact.content()))
                    .toList();
            CompletionCandidate completion = new CompletionCandidate(response.completion().complete(),
                    response.completion().resolvedObjectiveIds(), response.completion().satisfiedResolutionCriteriaIds(),
                    response.completion().concludingScene());
            return new GmPlanResult(plan, provider, model, reasoning, List.of(), List.of(),
                    situation(response.situation()), runtimeFacts, completion);
        } catch (RuntimeGmInputLimitException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("typed runtime GM interrupted", exception);
        } catch (Exception exception) {
            devLog("dev_agent_http operation=typed_runtime_gm outcome=exception turnId={} operationKey={} failureClass={} failure={}",
                    context.turnId(), context.operationKey(), exception.getClass().getName(), exception.getMessage());
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
        var selection = context.requestedSelection();
        String selectionBody = mapper.writeValueAsString(new RuntimeEndpointRequest(
                selection.endpointId(), selection.provider(), selection.model(), selection.reasoning()));
        HttpRequest selectionRequest = HttpRequest.newBuilder(baseUri.resolve("internal/gm/runtime-endpoint"))
                .timeout(timeout).header("Content-Type", "application/json")
                .header("X-Internal-Token", internalToken)
                .POST(HttpRequest.BodyPublishers.ofString(selectionBody)).build();
        String callContext = "turnId=" + context.turnId() + " operationKey=" + context.operationKey();
        long endpointStarted = com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics
                .begin(LOGGER, "runtime_endpoint_selection", callContext);
        HttpResponse<String> selected;
        try {
            selected = client.send(selectionRequest, HttpResponse.BodyHandlers.ofString());
            com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.complete(
                    LOGGER, "runtime_endpoint_selection", callContext + " status=" + selected.statusCode(), endpointStarted);
        } catch (Exception failure) {
            com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.fail(
                    LOGGER, "runtime_endpoint_selection", callContext, endpointStarted, failure);
            throw failure;
        }
        if (selected.statusCode() / 100 != 2) {
            throw new RuntimeGmInputLimitException("selected GM model is unavailable for input composition");
        }
        RuntimeEndpointResponse endpoint = mapper.readValue(selected.body(), RuntimeEndpointResponse.class);
        int contextLimit = contextLimits.require(endpoint.provider(), endpoint.model());
        long promptStarted = com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics
                .begin(LOGGER, "runtime_prompt_composition", callContext + " provider=" + endpoint.provider() + " model=" + endpoint.model());
        String prompt;
        try {
            prompt = context.composePrompt(contextLimit);
            com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.complete(
                    LOGGER, "runtime_prompt_composition", callContext + " contextLimit=" + contextLimit, promptStarted);
        } catch (RuntimeException | Error failure) {
            com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.fail(
                    LOGGER, "runtime_prompt_composition", callContext, promptStarted, failure);
            throw failure;
        }
        String body = mapper.writeValueAsString(new RuntimeRequest(context.ownerPlayerId().value(), context.operationKey(), context.action(),
                selection.endpointId(), selection.provider(), selection.model(), selection.reasoning(),
                endpoint.endpointId(), endpoint.endpointVersion(), endpoint.provider(), endpoint.model(), prompt,
                context.ragSearchContext()));
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("internal/gm/runtime-turn"))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("X-Internal-Token", internalToken)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        long runtimeStarted = com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics
                .begin(LOGGER, "runtime_agent_request", callContext + " provider=" + endpoint.provider() + " model=" + endpoint.model());
        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
            com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.complete(
                    LOGGER, "runtime_agent_request", callContext + " status=" + response.statusCode(), runtimeStarted);
        } catch (Exception failure) {
            com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.fail(
                    LOGGER, "runtime_agent_request", callContext, runtimeStarted, failure);
            throw failure;
        }
        if (response.statusCode() == 503) {
            throw new RuntimeGmInputLimitException("selected GM endpoint changed or input limit is unavailable");
        }
        if (response.statusCode() / 100 != 2) {
            devLog("dev_agent_http operation=typed_runtime_gm outcome=http_error turnId={} operationKey={} status={} response={}",
                    context.turnId(), context.operationKey(), response.statusCode(),
                    com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.safeBody(response.body()));
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
        devLog("dev_agent_response operation=typed_runtime_gm turnId={} operationKey={} scene={} situationKind={} situationLocation={} situationBasis={} situationReference={} combatStart={} enemyCount={} mapEntryRequested={}",
                context.turnId(), context.operationKey(), result.scene(), result.situation().kind(), result.situation().location(),
                result.situation().basis(), result.situation().reference(), result.combatStart(),
                result.combatEnemies().size(), result.mapEntryRequested());
        return result;
    }

    private static void devLog(String pattern, Object... args) {
        if (com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.enabled()) LOGGER.info(pattern, args);
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

    record RuntimeEndpointRequest(java.util.UUID endpointId, String provider, String model, String reasoning) { }
    record RuntimeEndpointResponse(java.util.UUID endpointId, String endpointVersion,
                                   String provider, String model, String reasoning) { }
    record RuntimeRequest(java.util.UUID soloPlayerId, String operationKey, String action,
                          java.util.UUID endpointId, String provider, String model, String reasoning,
                          java.util.UUID effectiveEndpointId, String effectiveEndpointVersion,
                          String effectiveProvider, String effectiveModel, String prompt,
                          java.util.Map<String, Object> ragSearchContext) { }
    record RuntimeResponse(String scene, String judgment, String narration, boolean combatStart,
                           List<CombatEnemyResponse> combatEnemies, SituationResponse situation,
                           boolean mapEntryRequested, List<RuntimeFactResponse> runtimeFacts,
                           CompletionResponse completion, List<String> citedEvidence,
                           @JsonProperty("판정제안") CheckProposalResponse checkProposal) {
        RuntimeResponse(String scene, String judgment, String narration, boolean combatStart,
                List<CombatEnemyResponse> combatEnemies, SituationResponse situation,
                boolean mapEntryRequested, List<RuntimeFactResponse> runtimeFacts) {
            this(scene, judgment, narration, combatStart, combatEnemies, situation, mapEntryRequested,
                    runtimeFacts, CompletionResponse.continueAdventure(), List.of(), CheckProposalResponse.none());
        }
        RuntimeResponse {
            runtimeFacts = runtimeFacts == null ? List.of() : List.copyOf(runtimeFacts);
            completion = completion == null ? CompletionResponse.continueAdventure() : completion;
            citedEvidence = citedEvidence == null ? List.of() : List.copyOf(citedEvidence);
            checkProposal = checkProposal == null ? CheckProposalResponse.none() : checkProposal;
        }
    }
    record CheckProposalResponse(@JsonProperty("필요") boolean required,
                                 @JsonProperty("이유") String reason,
                                 @JsonProperty("판정능력또는기술") String abilityOrSkill,
                                 @JsonProperty("대상캐릭터ID") java.util.UUID characterSheetId,
                                 @JsonProperty("굴림주체") String rollMethod,
                                 @JsonProperty("굴림식") String diceExpression,
                                 @JsonProperty("보정치") int modifier,
                                 @JsonProperty("난이도") Integer difficulty,
                                 @JsonProperty("근거키") List<String> evidenceKeys,
                                 @JsonProperty("성공시결과") String successOutcome,
                                 @JsonProperty("실패시결과") String failureOutcome) {
        CheckProposalResponse {
            evidenceKeys = evidenceKeys == null ? List.of() : List.copyOf(evidenceKeys);
        }
        static CheckProposalResponse none() {
            return new CheckProposalResponse(false, "", "", null, "", "", 0, null, List.of(), "", "");
        }
    }
    record CombatEnemyResponse(String mode, String scenarioId, String enemyKey, String name, int count,
            List<CombatEnemyAbilityResponse> abilities, List<CombatEnemyActionResponse> actions) {
        CombatEnemyResponse(String mode, String scenarioId, String enemyKey, String name, int count) {
            this(mode, scenarioId, enemyKey, name, count, List.of(), List.of());
        }
        CombatEnemyResponse {
            abilities = abilities == null ? List.of() : List.copyOf(abilities);
            actions = actions == null ? List.of() : List.copyOf(actions);
        }
    }
    record CombatEnemyAbilityResponse(String ability, int score, List<String> citationKeys) {
        CombatEnemyAbilityResponse { citationKeys = citationKeys == null ? List.of() : List.copyOf(citationKeys); }
    }
    record CombatEnemyActionResponse(String name, String description, List<String> citationKeys) {
        CombatEnemyActionResponse { citationKeys = citationKeys == null ? List.of() : List.copyOf(citationKeys); }
    }
    record SituationResponse(String kind, String location, String problem, String threat, String goal,
                             String basis, String reference, boolean required) { }
    record RuntimeFactResponse(String subject, String content) { }
    record CompletionResponse(boolean complete, List<String> resolvedObjectiveIds,
            List<String> satisfiedResolutionCriteriaIds, String concludingScene) {
        CompletionResponse {
            resolvedObjectiveIds = resolvedObjectiveIds == null ? List.of() : List.copyOf(resolvedObjectiveIds);
            satisfiedResolutionCriteriaIds = satisfiedResolutionCriteriaIds == null
                    ? List.of() : List.copyOf(satisfiedResolutionCriteriaIds);
            concludingScene = concludingScene == null ? "" : concludingScene.trim();
        }
        static CompletionResponse continueAdventure() {
            return new CompletionResponse(false, List.of(), List.of(), "");
        }
    }
}
