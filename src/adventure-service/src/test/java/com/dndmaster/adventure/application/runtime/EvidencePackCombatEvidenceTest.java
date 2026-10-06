package com.dndmaster.adventure.application.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EvidencePackCombatEvidenceTest {
    @Test
    void keeps_combat_search_results_in_their_original_source_groups() {
        RuntimeEvidence existingStory = evidence(RuntimeEvidenceType.STORYBOOK, "story-existing");
        RuntimeEvidence existingRule = evidence(RuntimeEvidenceType.RULEBOOK, "rule-existing");
        RuntimeEvidence combatStory = evidence(RuntimeEvidenceType.STORYBOOK, "story-combat-stats");
        RuntimeEvidence combatRule = evidence(RuntimeEvidenceType.RULEBOOK, "rule-combat-stats");
        EvidencePack initial = new EvidencePack(List.of(existingStory), List.of(existingRule), List.of());

        EvidencePack prioritized = initial.prioritizingCombatEvidence(List.of(combatStory, combatRule));

        assertThat(prioritized.storybook()).containsExactly(combatStory, existingStory);
        assertThat(prioritized.rulebook()).containsExactly(combatRule, existingRule);
        assertThat(prioritized.totalEvidenceCount()).isEqualTo(4);
    }

    @Test
    void combat_prioritization_keeps_all_evidence_when_the_combined_pack_exceeds_eight() {
        List<RuntimeEvidence> story = java.util.stream.IntStream.range(0, 6)
                .mapToObj(index -> evidence(RuntimeEvidenceType.STORYBOOK, "story-" + index)).toList();
        List<RuntimeEvidence> rules = java.util.stream.IntStream.range(0, 6)
                .mapToObj(index -> evidence(RuntimeEvidenceType.RULEBOOK, "rules-" + index)).toList();
        EvidencePack initial = new EvidencePack(story, rules, List.of());

        EvidencePack prioritized = initial.prioritizingCombatEvidence(List.of(
                evidence(RuntimeEvidenceType.RULEBOOK, "combat-rules")));

        assertThat(prioritized.totalEvidenceCount()).isEqualTo(13);
        assertThat(prioritized.rules()).hasSize(13);
    }

    private static RuntimeEvidence evidence(RuntimeEvidenceType type, String locator) {
        return new RuntimeEvidence(type, new KnowledgeDocumentId(UUID.randomUUID()), 1, locator, "excerpt");
    }
}
