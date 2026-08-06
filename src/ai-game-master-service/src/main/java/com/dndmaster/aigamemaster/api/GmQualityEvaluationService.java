package com.dndmaster.aigamemaster.api;

import com.dndmaster.aigamemaster.infrastructure.ai.GmCompletionAdapter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Executes quality scenarios through the configured provider and applies automated safety checks. */
public final class GmQualityEvaluationService {
    private final GmCompletionAdapter adapter;
    private final ObjectMapper mapper;

    public GmQualityEvaluationService(GmCompletionAdapter adapter, ObjectMapper mapper) {
        this.adapter = Objects.requireNonNull(adapter);
        this.mapper = Objects.requireNonNull(mapper);
    }

    public List<Result> evaluate(List<Scenario> scenarios) {
        Objects.requireNonNull(scenarios);
        if (scenarios.isEmpty()) throw new IllegalArgumentException("quality scenarios required");
        return scenarios.stream().map(this::evaluate).toList();
    }

    private Result evaluate(Scenario scenario) {
        try {
            GmAgentController.Response response = adapter.complete(
                    "quality:" + scenario.id(), scenario.prompt(), json -> parse(json));
            String serialized;
            try {
                serialized = mapper.writeValueAsString(response).toLowerCase(Locale.ROOT);
            } catch (JsonProcessingException failure) {
                throw new IllegalStateException("quality response could not be serialized", failure);
            }
            boolean secretLeak = scenario.protectedFacts().stream()
                    .map(value -> value.toLowerCase(Locale.ROOT)).anyMatch(serialized::contains);
            String citedEvidence;
            try {
                citedEvidence = mapper.writeValueAsString(response.citedEvidence()).toLowerCase(Locale.ROOT);
            } catch (JsonProcessingException failure) {
                throw new IllegalStateException("quality evidence could not be serialized", failure);
            }
            boolean evidenceCorrect = scenario.expectedEvidence().stream()
                    .map(value -> value.toLowerCase(Locale.ROOT)).allMatch(citedEvidence::contains);
            boolean forbiddenTool = response.toolCalls() != null && response.toolCalls().stream()
                    .filter(Objects::nonNull)
                    .anyMatch(call -> scenario.forbiddenTools().stream()
                            .anyMatch(forbidden -> forbidden.equalsIgnoreCase(call.toolName())));
            boolean inventedState = response.stateDelta() != null && !response.stateDelta().isEmpty();
            return new Result(scenario.id(), true, evidenceCorrect, !secretLeak && !inventedState,
                    secretLeak, forbiddenTool, inventedState, null);
        } catch (RuntimeException failure) {
            return new Result(scenario.id(), false, false, false, false, false, false,
                    failure.getClass().getSimpleName());
        }
    }

    private GmAgentController.Response parse(String json) {
        try {
            return GmAgentController.requireComplete(mapper.readValue(json, GmAgentController.Response.class));
        } catch (JsonProcessingException | IllegalArgumentException failure) {
            throw new com.dndmaster.aigamemaster.infrastructure.ai.ProviderMalformedResponseException(
                    "GM quality response invalid: " + failure.getMessage());
        }
    }

    public record Scenario(String id, String prompt, List<String> expectedEvidence,
                           List<String> expectedState, List<String> protectedFacts,
                           List<String> forbiddenTools) {
        public Scenario {
            id = required(id, "id"); prompt = required(prompt, "prompt");
            expectedEvidence = List.copyOf(Objects.requireNonNull(expectedEvidence));
            expectedState = List.copyOf(Objects.requireNonNull(expectedState));
            protectedFacts = List.copyOf(Objects.requireNonNull(protectedFacts));
            forbiddenTools = List.copyOf(Objects.requireNonNull(forbiddenTools));
        }
        private static String required(String value, String name) {
            if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " required");
            return value;
        }
    }

    public record Result(String id, boolean structuredSuccess, boolean ruleEvidenceCorrect,
                         boolean planFactConsistent, boolean secretLeak, boolean forbiddenTool,
                         boolean inventedState, String failure) {}
}
