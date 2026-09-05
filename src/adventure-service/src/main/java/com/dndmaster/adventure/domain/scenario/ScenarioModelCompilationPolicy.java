package com.dndmaster.adventure.domain.scenario;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Owns compilation input validation, source precedence, and READY/BLOCKED policy. */
public final class ScenarioModelCompilationPolicy {
    private ScenarioModelCompilationPolicy() {}

    public static Evaluation evaluate(ScenarioCompilationInputSnapshot input, ScenarioModel model) {
        Objects.requireNonNull(input, "compilation input is required");
        Objects.requireNonNull(model, "scenario model is required");
        List<ScenarioCompilationDiagnostic> diagnostics = new ArrayList<>(input.validate());
        ScenarioModel effectiveModel = model;
        if (!model.hasCoreResolutionInformation() && diagnostics.stream().noneMatch(diagnostic ->
                diagnostic.severity() == ScenarioCompilationDiagnostic.Severity.BLOCKING)) {
            effectiveModel = model.withGeneratedCore(input.creativity());
        }
        if (!effectiveModel.hasCoreResolutionInformation()) {
            diagnostics.add(ScenarioCompilationDiagnostic.blocking("CORE_RESOLUTION_MISSING",
                    input.creativity() == ScenarioCreativity.NONE
                            ? "Creativity NONE cannot fill missing objective, starting situation, or resolution condition"
                            : "objective, starting situation, and resolution condition are required"));
        }
        boolean blocked = diagnostics.stream().anyMatch(diagnostic -> diagnostic.severity() == ScenarioCompilationDiagnostic.Severity.BLOCKING);
        return new Evaluation(blocked ? Status.BLOCKED : Status.READY, effectiveModel, List.copyOf(diagnostics));
    }

    public static PrecedenceResolution resolveObjective(
            ScenarioCompilationInputSnapshot input, List<ScenarioModelElement> candidates) {
        Objects.requireNonNull(input, "compilation input is required");
        List<ScenarioModelElement> ordered = List.copyOf(Objects.requireNonNull(candidates, "candidates are required"));
        ScenarioModelElement selected = ordered.stream()
                .sorted(Comparator.comparingInt((ScenarioModelElement element) -> precedence(input, element)).reversed())
                .findFirst().orElseThrow(() -> new IllegalArgumentException("at least one objective candidate is required"));
        List<String> discarded = ordered.stream().filter(element -> element != selected).map(ScenarioModelElement::elementId).toList();
        return new PrecedenceResolution(selected, discarded);
    }

    private static int precedence(ScenarioCompilationInputSnapshot input, ScenarioModelElement element) {
        String source = String.valueOf(element.attributes().getOrDefault("source", "")).toUpperCase();
        if (input.integrationPrompt() != null && !input.integrationPrompt().isBlank() && source.equals("INTEGRATION_PROMPT")) return 3;
        if (input.primaryStorybookId() != null && source.equals("PRIMARY")
                && element.sourceRefs().stream().anyMatch(ref -> ref.knowledgeDocumentId().value().equals(input.primaryStorybookId()))) return 2;
        if (source.equals("PRIMARY")) return 2;
        return 1;
    }

    public enum Status { READY, BLOCKED }
    public record Evaluation(Status status, ScenarioModel model, List<ScenarioCompilationDiagnostic> diagnostics) {
        public Evaluation {
            status = Objects.requireNonNull(status, "status is required");
            model = Objects.requireNonNull(model, "model is required");
            diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics are required"));
        }
    }
    public record PrecedenceResolution(ScenarioModelElement selected, List<String> discardedSourceIds) {
        public PrecedenceResolution {
            selected = Objects.requireNonNull(selected, "selected element is required");
            discardedSourceIds = List.copyOf(Objects.requireNonNull(discardedSourceIds, "discarded source ids are required"));
        }
    }
}
