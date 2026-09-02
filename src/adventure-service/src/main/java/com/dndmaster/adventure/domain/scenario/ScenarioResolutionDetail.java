package com.dndmaster.adventure.domain.scenario;

import java.util.List;

public record ScenarioResolutionDetail(
        TriggerContract trigger,
        CheckContract check,
        StateEffect stateEffect,
        RevealContract reveal,
        PriorKnowledge priorKnowledge,
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

    public ScenarioResolutionDetail(String triggerCondition, String actor, String roller,
            String instructionVisibility, String resultVisibility, List<String> modifiers,
            String advantageState, String reroll, List<Step> steps, List<Outcome> outcomes,
            List<TableEntry> randomTable, String tableCoverage) {
        this(null, null, null, null, null, triggerCondition, actor, roller, instructionVisibility,
                resultVisibility, modifiers, advantageState, reroll, steps, outcomes, randomTable, tableCoverage);
    }

    public static ScenarioResolutionDetail empty() {
        return new ScenarioResolutionDetail(null, null, null, null, null, null, null, null, null, null,
                List.of(), null, null, List.of(), List.of(), List.of(), null);
    }

    /**
     * Projects canonical fields into the fields retained by the pre-canonical API.
     * Only facts represented by the typed contract or candidate visibility are copied;
     * missing canonical data is never synthesized.
     */
    public ScenarioResolutionDetail withLegacyProjection(ResolutionVisibility candidateVisibility) {
        return new ScenarioResolutionDetail(
                trigger, check, stateEffect, reveal, priorKnowledge,
                present(triggerCondition) ? triggerCondition : trigger == null ? null : trigger.condition(),
                present(actor) ? actor : trigger == null || trigger.type() == null ? null : trigger.type().name(),
                present(roller) ? roller : check == null || check.rollMethod() == null ? null : check.rollMethod().name(),
                present(instructionVisibility) ? instructionVisibility
                        : candidateVisibility == null ? null : candidateVisibility.name(),
                resultVisibility, modifiers, advantageState, reroll, steps, outcomes, randomTable, tableCoverage);
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }

    public boolean isEmpty() {
        return trigger == null
                && check == null
                && stateEffect == null
                && reveal == null
                && priorKnowledge == null
                && triggerCondition == null
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

    public enum TriggerType { WORLD_EVENT, PLAYER_ACTION }
    public enum RollMethod { SYSTEM, PLAYER }
    public enum RevealCondition { ON_SUCCESS, ON_FAILURE, ALWAYS }
    public enum RevealLevel { NONE, CLUE, FACT, FULL }

    public record TriggerContract(TriggerType type, String condition) { }
    public record CheckContract(RollMethod rollMethod, String method) { }
    public record StateEffect(String stateKey, String successEffect, String failureEffect) { }
    public record RevealContract(RevealCondition condition, RevealLevel level, String hiddenFact) { }
    public record PriorKnowledge(boolean alreadyPublic, List<String> knownFacts) {
        public PriorKnowledge {
            knownFacts = knownFacts == null ? List.of() : List.copyOf(knownFacts);
        }
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
