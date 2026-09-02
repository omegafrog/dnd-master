package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.adventure.AdventureContext;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Verifies that a player-facing resolved turn has intent and advances the fiction. */
public final class MeaningfulProgressPolicy {
    public MeaningfulProgress evaluate(String playerIntent, TurnPlan plan,
                                        AdventureContext previousContext, List<String> outcomes) {
        return evaluate(playerIntent, plan, previousContext, outcomes, false);
    }

    public MeaningfulProgress evaluate(String playerIntent, TurnPlan plan,
                                        AdventureContext previousContext, List<String> outcomes,
                                        boolean advancesStoryPlan) {
        if (playerIntent == null || playerIntent.isBlank()) reject("player intent is missing");
        Objects.requireNonNull(plan, "turn plan must not be null");
        Objects.requireNonNull(previousContext, "previous context must not be null");
        List<String> safeOutcomes = outcomes == null ? List.of() : outcomes.stream()
                .filter(value -> value != null && !value.isBlank()).map(String::trim).toList();

        String judgment = plan.judgment().trim();
        if (isDecisionRequired(judgment) && !hasConcreteChoice(judgment)) {
            reject("DECISION_REQUIRED has no concrete choices");
        }

        EnumSet<MeaningfulProgressCategory> categories = EnumSet.noneOf(MeaningfulProgressCategory.class);
        if (!plan.scene().equals(previousContext.currentScene())
                || !plan.npcState().equals(normalize(previousContext.npcState()))) {
            categories.add(MeaningfulProgressCategory.WORLD_STATE);
        }
        if (!plan.revealableFacts().isEmpty()) categories.add(MeaningfulProgressCategory.INFORMATION);
        if (safeOutcomes.stream().anyMatch(outcome -> !sameMeaning(outcome, previousContext.latestJudgment()))) {
            categories.add(MeaningfulProgressCategory.OUTCOME);
        }
        if (isDecisionRequired(judgment) && hasConcreteChoice(judgment)) {
            categories.add(MeaningfulProgressCategory.DECISION);
        }
        // A grounded check request deliberately leaves its result unresolved.
        // It still advances the interaction by handing the player a distinct
        // next decision, and must not be rejected as a repeated narration.
        if (isCheckRequired(judgment)) categories.add(MeaningfulProgressCategory.CHECK);
        if (advancesStoryPlan) categories.add(MeaningfulProgressCategory.PROGRESS);
        if (categories.isEmpty()) reject("NO_MEANINGFUL_PROGRESS");
        return MeaningfulProgress.of(categories);
    }

    private static boolean isDecisionRequired(String judgment) {
        String normalized = judgment.toUpperCase(Locale.ROOT).replace('-', '_');
        return normalized.contains("DECISION_REQUIRED") || normalized.contains("판단_REQUIRED")
                || normalized.contains("선택이 필요") || normalized.contains("선택지 필요");
    }

    private static boolean hasConcreteChoice(String judgment) {
        String normalized = judgment.toLowerCase(Locale.ROOT);
        return normalized.matches("(?s).*([a-z][).:]|\\b[12][).:]|\\bchoose\\b|\\boption\\b|\\bchoice\\b|선택지|고르|선택).*.*")
                || normalized.contains(" 또는 ") || normalized.contains(" vs ");
    }

    private static boolean isCheckRequired(String judgment) {
        String normalized = judgment.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        return normalized.contains("판정이 필요")
                || normalized.contains("판정 필요")
                || normalized.contains("굴림이 필요")
                || normalized.contains("check required");
    }

    private static boolean sameMeaning(String left, String right) {
        return normalize(left).equals(normalize(right));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static void reject(String reason) {
        throw new IllegalStateException(reason.startsWith("NO_MEANINGFUL_PROGRESS")
                || reason.startsWith("DECISION_REQUIRED") ? reason : "NO_MEANINGFUL_PROGRESS: " + reason);
    }
}
