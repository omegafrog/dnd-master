package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.*;

import com.dndmaster.adventure.application.runtime.*;
import com.dndmaster.adventure.domain.adventure.AdventureContext;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import java.util.*;
import org.junit.jupiter.api.Test;

class GmCitationBindingTest {
    private final RuntimeEvidence story = new RuntimeEvidence(RuntimeEvidenceType.STORYBOOK,
            new KnowledgeDocumentId(UUID.randomUUID()), 7, "page:4", "지하실에는 거대 쥐 두 마리가 있습니다.", "rat-fact");
    private final EvidencePack pack = new EvidencePack(List.of(story), List.of(), List.of());
    private final AdventureContext context = new AdventureContext("cellar", "guard", null, null);

    @Test
    void accepts_korean_paraphrase_when_binding_key_is_a_member_and_supported() {
        RuntimePlan plan = plan("지하실에는 거대한 쥐 두 마리가 있다.",
                new GmCitationBinding("지하실에는 거대한 쥐 두 마리가 있다.", "narration", "rat-fact"));
        assertDoesNotThrow(() -> new GmFinalValidator().validate(
                new GmPlanResult(plan, "ollama", "model", "", List.of()), pack, context, Set.of()));
        assertEquals(1, new GmFinalValidator().validateReport(
                new GmPlanResult(plan, "ollama", "model", "", List.of()), pack, context, Set.of()).claimSupportCount());
    }

    @Test
    void reports_outside_and_unrelated_bindings_without_collapsing_to_generic_error() {
        RuntimeEvidence unrelated = new RuntimeEvidence(RuntimeEvidenceType.STORYBOOK, story.knowledgeDocumentId(), 7,
                "page:9", "항구에는 폭풍이 옵니다.", "storm-fact");
        EvidencePack expanded = new EvidencePack(List.of(story, unrelated), List.of(), List.of());
        RuntimePlan plan = plan("경비병은 열쇠를 건넨다.",
                new GmCitationBinding("경비병은 열쇠를 건넨다.", "narration", "storm-fact"));

        GmValidationReport report = new GmFinalValidator().validateReport(
                new GmPlanResult(plan, "ollama", "model", "", List.of()), expanded, context, Set.of());
        assertEquals(Set.of("UNSUPPORTED_CLAIM_CITATION"), report.violations().stream().map(GmValidationViolation::code).collect(java.util.stream.Collectors.toSet()));
        IllegalStateException failure = assertThrows(IllegalStateException.class, () ->
                new GmFinalValidator().validate(new GmPlanResult(plan, "ollama", "model", "", List.of()), expanded, context, Set.of()));
        assertTrue(failure.getMessage().contains("UNSUPPORTED_CLAIM_CITATION"));
    }

    @Test
    void rejects_citation_key_that_is_not_in_the_pack() {
        RuntimePlan plan = plan("지하실에는 거대 쥐가 있다.",
                new GmCitationBinding("지하실에는 거대 쥐가 있다.", "narration", "invented"));
        GmValidationReport report = new GmFinalValidator().validateReport(
                new GmPlanResult(plan, "ollama", "model", "", List.of()), pack, context, Set.of());
        assertTrue(report.violations().stream().anyMatch(v -> v.code().equals("CITATION_NOT_IN_EVIDENCE_PACK")));
    }

    private RuntimePlan plan(String narration, GmCitationBinding binding) {
        return new RuntimePlan("cellar", "guard", "judgment", narration, null, List.of(story), List.of(),
                "ollama", "model", "", false, "", List.of(binding));
    }
}
