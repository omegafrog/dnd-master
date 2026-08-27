package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.application.quality.GoldenJourneyFailureSnapshot;
import com.dndmaster.adventure.application.quality.GoldenJourneyQualityEvaluator;
import com.dndmaster.adventure.application.quality.GoldenJourneyQualityReport;
import com.dndmaster.adventure.application.quality.GoldenJourneyTurn;
import com.dndmaster.adventure.domain.runtime.EffectiveGmProviderSelection;
import com.dndmaster.adventure.domain.runtime.RequestedGmProviderSelection;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class Rag026GoldenJourneyContractTest {
    private static final UUID ENDPOINT = UUID.fromString("00000000-0000-0000-0000-000000000026");
    private static final Instant ENDPOINT_VERSION = Instant.parse("2026-08-27T00:00:00Z");
    private static final String EXTRACTION_VERSION = "extraction-rag-026";
    private static final Set<String> PUBLISHED_CHUNK_IDS = Set.of(
            "chunk-1", "chunk-2", "chunk-3", "chunk-4", "chunk-5");

    @Test
    void release_gate_requires_five_distinct_grounded_turns_and_zero_neutral_fallback() {
        RequestedGmProviderSelection requested = new RequestedGmProviderSelection(
                ENDPOINT, "ollama", "qwen3:8b", "medium");
        EffectiveGmProviderSelection effective = new EffectiveGmProviderSelection(
                ENDPOINT, ENDPOINT_VERSION, "ollama", "qwen3:8b", "medium");

        List<GoldenJourneyTurn> turns = IntStream.rangeClosed(1, 5)
                .mapToObj(number -> new GoldenJourneyTurn(
                        "turn-" + number,
                        "실제 플레이어 입력 " + number,
                        true,
                        false,
                        1,
                        1,
                        requested,
                        effective,
                        effective,
                        100L + number,
                        List.of("chunk-" + number),
                        EXTRACTION_VERSION))
                .toList();

        GoldenJourneyQualityReport report = new GoldenJourneyQualityEvaluator().evaluate(turns, 500L);

        assertEquals(5, report.totalTurns());
        assertEquals(5, report.distinctInputCount());
        assertEquals(1.0, report.actionReflectionRate());
        assertEquals(0.0, report.neutralFallbackRate());
        assertEquals(1.0, report.citationExactness());
        assertEquals(1.0, report.citationRelevance());
        assertEquals(1.0, report.providerMatchRate());
        assertTrue(report.requestedEffectiveActualAudit().stream()
                .allMatch(item -> item.actualMatchesEffective()));
        assertTrue(report.passed());
    }

    @Test
    void deterministic_fixture_reproduces_the_same_five_turn_report_and_published_provenance() {
        RequestedGmProviderSelection requested = new RequestedGmProviderSelection(
                ENDPOINT, "ollama", "qwen3:8b", "medium");
        EffectiveGmProviderSelection effective = new EffectiveGmProviderSelection(
                ENDPOINT, ENDPOINT_VERSION, "ollama", "qwen3:8b", "medium");

        List<GoldenJourneyTurn> first = deterministicTurns(requested, effective);
        List<GoldenJourneyTurn> second = deterministicTurns(requested, effective);

        assertEquals(first, second);
        assertTrue(first.stream().allMatch(turn -> turn.extractionVersion().equals(EXTRACTION_VERSION)
                && turn.citationChunkIds().stream().allMatch(PUBLISHED_CHUNK_IDS::contains)));
        assertEquals(
                new GoldenJourneyQualityEvaluator().evaluate(first, 500L),
                new GoldenJourneyQualityEvaluator().evaluate(second, 500L));
    }

    @Test
    void quality_gate_rejects_duplicate_inputs_neutral_fallback_and_latency_over_budget() {
        RequestedGmProviderSelection selection = new RequestedGmProviderSelection(
                ENDPOINT, "ollama", "qwen3:8b", "medium");
        EffectiveGmProviderSelection effective = new EffectiveGmProviderSelection(
                ENDPOINT, ENDPOINT_VERSION, "ollama", "qwen3:8b", "medium");
        GoldenJourneyTurn turn = new GoldenJourneyTurn(
                "duplicate", "같은 입력", false, true, 0, 0, selection, effective, effective,
                501L, List.of(), "extraction-rag-026");

        GoldenJourneyQualityReport report = new GoldenJourneyQualityEvaluator().evaluate(
                List.of(turn, turn, turn, turn, turn), 500L);

        assertEquals(1, report.distinctInputCount());
        assertFalse(report.passed());
        assertEquals(0.0, report.actionReflectionRate());
        assertEquals(1.0, report.neutralFallbackRate());
        assertEquals(1.0, report.latencyOverBudgetRate());
    }

    @Test
    void provider_quality_metric_requires_requested_effective_and_actual_identity_to_align() {
        RequestedGmProviderSelection requested = new RequestedGmProviderSelection(
                ENDPOINT, "ollama", "qwen3:8b", "medium");
        EffectiveGmProviderSelection effective = new EffectiveGmProviderSelection(
                ENDPOINT, ENDPOINT_VERSION, "openai", "gpt-5.6-luna", "medium");
        GoldenJourneyTurn turn = new GoldenJourneyTurn(
                "turn-1", "규칙을 확인해줘", true, false, 1, 1,
                requested, effective, effective, 100L, List.of("chunk-1"), "extraction-rag-026");

        GoldenJourneyQualityReport report = new GoldenJourneyQualityEvaluator().evaluate(List.of(turn), 500L);

        assertEquals(0.0, report.providerMatchRate());
        assertFalse(report.requestedEffectiveActualAudit().getFirst().requestedMatchesEffective());
        assertTrue(report.requestedEffectiveActualAudit().getFirst().actualMatchesEffective());
        assertFalse(report.passed());
    }

    @Test
    void forced_failure_snapshot_keeps_adventure_version_conversation_and_stage_immutable() {
        GoldenJourneyFailureSnapshot before = new GoldenJourneyFailureSnapshot(
                7L, "conversation-before", "rat-combat");
        GoldenJourneyFailureSnapshot after = before.failedRetryable("provider-timeout");

        assertEquals(before.adventureVersion(), after.adventureVersion());
        assertEquals(before.conversation(), after.conversation());
        assertEquals(before.stageKey(), after.stageKey());
        assertEquals("provider-timeout", after.failureCode());
        assertTrue(after.isImmutableComparedTo(before));
    }

    @Test
    void turn_contract_rejects_missing_published_provenance() {
        RequestedGmProviderSelection selection = new RequestedGmProviderSelection(
                ENDPOINT, "ollama", "qwen3:8b", "medium");
        EffectiveGmProviderSelection effective = new EffectiveGmProviderSelection(
                ENDPOINT, ENDPOINT_VERSION, "ollama", "qwen3:8b", "medium");

        assertThrows(IllegalArgumentException.class, () -> new GoldenJourneyTurn(
                "turn-1", "행동", true, false, 1, 1, selection, effective, effective,
                100L, List.of("chunk-1"), ""));
    }

    private static List<GoldenJourneyTurn> deterministicTurns(
            RequestedGmProviderSelection requested, EffectiveGmProviderSelection effective) {
        return IntStream.rangeClosed(1, 5)
                .mapToObj(number -> new GoldenJourneyTurn(
                        "turn-" + number,
                        "실제 플레이어 입력 " + number,
                        true,
                        false,
                        1,
                        1,
                        requested,
                        effective,
                        effective,
                        100L + number,
                        List.of("chunk-" + number),
                        EXTRACTION_VERSION))
                .toList();
    }
}
