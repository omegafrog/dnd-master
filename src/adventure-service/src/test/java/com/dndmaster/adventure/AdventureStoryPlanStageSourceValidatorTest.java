package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.application.storyplan.AdventureStoryPlanGenerationPort;
import com.dndmaster.adventure.application.storyplan.AdventureStoryPlanStageSourceValidator;
import com.dndmaster.adventure.domain.adventure.AdventureGroundingStatus;
import com.dndmaster.adventure.domain.adventure.AdventurePlanEvidence;
import com.dndmaster.adventure.domain.adventure.AdventureStageType;
import com.dndmaster.adventure.domain.adventure.AdventureStoryPlanStage;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdventureStoryPlanStageSourceValidatorTest {
    @Test
    void rejectsUnsupportedBossRewardEndingAndTransitionFactsDespiteAValidCitation() {
        var citation = citation("The cellar contains a rat swarm and an exit by the stairs.");
        var stage = stage(citation, "ancient dragon", List.of("royal crown"),
                "inherit the kingdom", "coronation-ending");

        var violations = new AdventureStoryPlanStageSourceValidator().validate(stage, List.of(citation));

        assertTrue(violations.contains("story stage boss is not supported by source evidence"));
        assertTrue(violations.contains("story stage reward is not supported by source evidence: royal crown"));
        assertTrue(violations.contains("story stage transition is not supported by source evidence"));
        assertTrue(violations.contains("story stage ending is not supported by source evidence: coronation-ending"));
    }

    @Test
    void acceptsCoreStageFactsThatAreSupportedByAuthoritativeEvidence() {
        var citation = citation("A rat swarm serves the ancient dragon guarding the royal crown. The party can inherit the kingdom and reach the coronation ending.");
        var stage = stage(citation, "ancient dragon", List.of("royal crown"),
                "inherit the kingdom", "coronation-ending");

        assertTrue(new AdventureStoryPlanStageSourceValidator().validate(stage, List.of(citation)).isEmpty());
    }

    @Test
    void doesNotTreatPartialTokenMatchesAsSourceSupport() {
        var citation = citation("A pirate captain guards the royal crown. The party can inherit the kingdom and reach the coronation ending.");
        var stage = stage(citation, "rat", List.of("royal crown"),
                "inherit the kingdom", "coronation-ending");

        var violations = new AdventureStoryPlanStageSourceValidator().validate(stage, List.of(citation));

        assertTrue(violations.contains("story stage boss is not supported by source evidence"));
    }

    @Test
    void rejectsUnsupportedStructuredNpcAndEnemyIdentitiesBeforeTheyBecomeAuthoritative() {
        var citation = citation("A rat swarm guards the royal crown. The party can inherit the kingdom and reach the coronation ending.");
        var stage = stage(citation, "", List.of("royal crown"),
                "inherit the kingdom", "coronation-ending",
                List.of("invented guide"), List.of("lich"));

        var violations = new AdventureStoryPlanStageSourceValidator().validate(stage, List.of(citation));

        assertTrue(violations.contains("story stage NPC or clue is not supported by source evidence: invented guide"));
        assertTrue(violations.contains("story stage enemy is not supported by source evidence: lich"));
    }

    @Test
    void rejectsPlanWhenRulebookIsAvailableButNeverSelectedAsEvidence() {
        var storybook = citation("The cellar contains a rat swarm and an exit by the stairs.");
        var rulebook = new AdventureStoryPlanGenerationPort.SourceCitation(
                "RULEBOOK", UUID.randomUUID(), 1, "page:12", "A rat swarm uses the standard swarm rules.", 1.0);

        var violations = new AdventureStoryPlanStageSourceValidator()
                .validateCitationCoverage(List.of(stage(storybook, "", List.of(), "move onward", "ending-1")),
                        List.of(storybook, rulebook));

        assertTrue(violations.contains("story plan must cite at least one RULEBOOK source"));
    }

    private static AdventureStoryPlanGenerationPort.SourceCitation citation(String quote) {
        return new AdventureStoryPlanGenerationPort.SourceCitation(
                "STORYBOOK", UUID.randomUUID(), 1, "page:1", quote, 1.0);
    }

    private static AdventureStoryPlanStage stage(
            AdventureStoryPlanGenerationPort.SourceCitation citation,
            String boss,
            List<String> rewards,
            String transition,
            String ending) {
        return stage(citation, boss, rewards, transition, ending, List.of(), List.of("rat swarm"));
    }

    private static AdventureStoryPlanStage stage(
            AdventureStoryPlanGenerationPort.SourceCitation citation,
            String boss,
            List<String> rewards,
            String transition,
            String ending,
            List<String> npcOrClues,
            List<String> enemies) {
        var evidence = new AdventurePlanEvidence(
                citation.documentType(), citation.documentId(), citation.extractionVersion(),
                citation.locator(), citation.quote(), citation.confidence());
        return new AdventureStoryPlanStage(
                1, "Cellar", "Clear the cellar", "Rats attack", transition,
                npcOrClues, List.of(ending), List.of(), AdventureStageType.DUNGEON,
                "Cellar", UUID.randomUUID(), "brewery", "page 1", enemies, boss,
                transition, "", rewards, List.of(ending), List.of(evidence),
                AdventureGroundingStatus.GROUNDED, List.of(), "SAFE", .9);
    }
}
