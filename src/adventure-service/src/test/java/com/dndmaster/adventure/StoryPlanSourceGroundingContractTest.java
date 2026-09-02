package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.application.storyplan.StoryPlanStructuralGuard;
import com.dndmaster.adventure.domain.adventure.AdventureStoryPlanStage;
import com.dndmaster.adventure.domain.adventure.ClaimOrigin;
import com.dndmaster.adventure.domain.adventure.SourceConstraint;
import com.dndmaster.adventure.domain.adventure.SourceConstraintPack;
import com.dndmaster.adventure.domain.adventure.SourceFactClaim;
import com.dndmaster.adventure.domain.adventure.StoryPlanGenerationMode;
import java.util.List;
import org.junit.jupiter.api.Test;

class StoryPlanSourceGroundingContractTest {
    @Test
    void source_claim_requires_citation_but_generated_detail_does_not() {
        assertThrows(IllegalArgumentException.class,
                () -> new SourceFactClaim("combatSkeleton.participants[0].name", "고블린", List.of(), ClaimOrigin.SOURCE));
        assertDoesNotThrow(() -> new SourceFactClaim("stages[0].goal", "파티가 문을 연다", List.of(), ClaimOrigin.GENERATED));
    }

    @Test
    void storybook_constraints_win_and_rulebook_constraints_remain_separate() {
        SourceConstraintPack pack = new SourceConstraintPack(
                List.of(new SourceConstraint("story-1", "stages[0].location", "폐허", List.of("story-citation"))),
                List.of(new SourceConstraint("rule-1", "stages[0].location", "던전", List.of("rule-citation")),
                        new SourceConstraint("rule-2", "stages[0].combat", "전투 규칙", List.of("rule-citation"))));

        assertEquals(List.of("폐허", "전투 규칙"), pack.effectiveConstraints().stream()
                .map(SourceConstraint::normalizedClaim).toList());
        assertEquals(List.of("던전", "전투 규칙"), pack.rulebookConstraints().stream()
                .map(SourceConstraint::normalizedClaim).toList());
    }

    @Test
    void generation_mode_is_explicitly_selected_by_storybook_presence() {
        assertEquals(StoryPlanGenerationMode.SOURCE_BOUND,
                StoryPlanGenerationMode.fromDocumentTypes(List.of("RULEBOOK", "STORYBOOK")));
        assertEquals(StoryPlanGenerationMode.GENERATIVE,
                StoryPlanGenerationMode.fromDocumentTypes(List.of("RULEBOOK")));
    }

    @Test
    void structural_guard_accepts_generated_detail_and_rejects_unbound_source_claim() {
        AdventureStoryPlanStage generated = new AdventureStoryPlanStage(1, "시작", "목표", "갈등", "다음", List.of(), List.of("ending-1"))
                .withSourceFactClaims(List.of(new SourceFactClaim("stages[0].goal", "파티가 문을 연다", List.of(), ClaimOrigin.GENERATED)));
        assertTrue(new StoryPlanStructuralGuard().validate(List.of(generated)).isEmpty());

        AdventureStoryPlanStage source = new AdventureStoryPlanStage(1, "시작", "목표", "갈등", "다음", List.of(), List.of("ending-1"));
        assertTrue(new StoryPlanStructuralGuard().validate(List.of(source.withSourceFactClaims(
                List.of(new SourceFactClaim("stages[0].goal", "문이 있다", List.of("citation-1"), ClaimOrigin.SOURCE)))))
                .stream().anyMatch(item -> item.contains("unknown claim citation key")));
    }
}
