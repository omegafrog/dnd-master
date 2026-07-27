package com.dndmaster.adventure.domain.scenario;

import java.util.List;

public record ScenarioResolutionDetail(
        String triggerCondition,
        String actor,
        String roller,
        String instructionVisibility,
        String resultVisibility,
        List<String> modifiers,
        String advantageState,
        String reroll,
        List<Step> steps,
        List<Outcome> outcomes,
        List<TableEntry> randomTable,
        String tableCoverage) {
    public ScenarioResolutionDetail {
        modifiers = modifiers == null ? List.of() : List.copyOf(modifiers);
        steps = steps == null ? List.of() : List.copyOf(steps);
        outcomes = outcomes == null ? List.of() : List.copyOf(outcomes);
        randomTable = randomTable == null ? List.of() : List.copyOf(randomTable);
    }

    public static ScenarioResolutionDetail empty() {
        return new ScenarioResolutionDetail(null, null, null, null, null, List.of(), null, null, List.of(), List.of(), List.of(), null);
    }

    public boolean isEmpty() {
        return triggerCondition == null
                && actor == null
                && roller == null
                && instructionVisibility == null
                && resultVisibility == null
                && modifiers.isEmpty()
                && advantageState == null
                && reroll == null
                && steps.isEmpty()
                && outcomes.isEmpty()
                && randomTable.isEmpty()
                && tableCoverage == null;
    }

    public record Step(
            String id,
            ResolutionKind kind,
            String abilityOrSkill,
            Integer dc,
            String diceExpression,
            String condition,
            List<String> nextStepIds,
            List<String> successOutcomeIds,
            List<String> failureOutcomeIds,
            List<ScenarioSourceReference> sourceRefs) {
        public Step {
            nextStepIds = nextStepIds == null ? List.of() : List.copyOf(nextStepIds);
            successOutcomeIds = successOutcomeIds == null ? List.of() : List.copyOf(successOutcomeIds);
            failureOutcomeIds = failureOutcomeIds == null ? List.of() : List.copyOf(failureOutcomeIds);
            sourceRefs = sourceRefs == null ? List.of() : List.copyOf(sourceRefs);
        }
    }

    public record Outcome(String id, String label, String description, List<ScenarioSourceReference> sourceRefs) {
        public Outcome {
            sourceRefs = sourceRefs == null ? List.of() : List.copyOf(sourceRefs);
        }
    }

    public record TableEntry(String range, String outcome, List<ScenarioSourceReference> sourceRefs) {
        public TableEntry {
            sourceRefs = sourceRefs == null ? List.of() : List.copyOf(sourceRefs);
        }
    }
}
