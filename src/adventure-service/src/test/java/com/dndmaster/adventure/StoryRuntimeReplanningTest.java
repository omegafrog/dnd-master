package com.dndmaster.adventure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.domain.runtime.story.FutureSuffixReplanningPolicy;
import com.dndmaster.adventure.domain.runtime.story.PlayCreatedStoryFact;
import com.dndmaster.adventure.domain.runtime.story.PremiseInvalidationAudit;
import com.dndmaster.adventure.domain.runtime.story.PremiseInvalidationPolicy;
import com.dndmaster.adventure.domain.runtime.story.PremiseInvalidationProposal;
import com.dndmaster.adventure.domain.runtime.story.PremiseInvalidationResult;
import com.dndmaster.adventure.domain.runtime.story.SituationAction;
import com.dndmaster.adventure.domain.runtime.story.StoryRuntimeProposal;
import com.dndmaster.adventure.domain.runtime.story.StoryRuntimeRules;
import com.dndmaster.adventure.domain.runtime.story.StoryRuntimeState;
import com.dndmaster.adventure.domain.scenario.DetailedStage;
import com.dndmaster.adventure.domain.scenario.FunnelDefinition;
import com.dndmaster.adventure.domain.scenario.PressureDefinition;
import com.dndmaster.adventure.domain.scenario.RevelationDefinition;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceReference;
import com.dndmaster.adventure.domain.scenario.SituationDefinition;
import com.dndmaster.adventure.domain.scenario.StageBackbone;
import com.dndmaster.adventure.domain.scenario.StageBackboneEntry;
import com.dndmaster.adventure.domain.scenario.StageIntent;
import com.dndmaster.adventure.domain.scenario.ThreatDefinition;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StoryRuntimeReplanningTest {
    private final UUID packageId = UUID.randomUUID();
    private final ScenarioSourceReference evidence = new ScenarioSourceReference(
            new KnowledgeDocumentId(UUID.randomUUID()), 1, "page:1:span:1");

    @Test
    void accepts_play_created_fact_once_and_replayed_proposal_is_idempotent() {
        DetailedStage current = stage("stage-1", 1);
        UUID proposalId = UUID.randomUUID();
        PlayCreatedStoryFact fact = new PlayCreatedStoryFact(UUID.randomUUID(), "The heir joined the party", UUID.randomUUID());
        StoryRuntimeProposal proposal = new StoryRuntimeProposal(proposalId, 0, packageId, 1, "stage-1", 1,
                SituationAction.none(), List.of(), List.of(), List.of(), null, List.of(fact));

        StoryRuntimeState accepted = StoryRuntimeRules.apply(StoryRuntimeState.start(current), current, proposal);
        StoryRuntimeState replayed = StoryRuntimeRules.apply(accepted, current, proposal.withExpectedVersion(accepted.version()));

        assertThat(accepted.acceptedStoryFacts()).containsExactly(fact);
        assertThat(replayed.version()).isEqualTo(accepted.version());
        assertThat(replayed.acceptedStoryFacts()).containsExactly(fact);
    }

    @Test
    void accepts_only_future_unstarted_premise_invalidation_supported_by_accepted_fact() {
        StageBackbone backbone = backbone();
        StoryRuntimeState state = StoryRuntimeState.start(stage("stage-1", 1));
        PlayCreatedStoryFact fact = new PlayCreatedStoryFact(UUID.randomUUID(), "The heir joined the party", UUID.randomUUID());
        state = StoryRuntimeRules.apply(state, stage("stage-1", 1),
                new StoryRuntimeProposal(UUID.randomUUID(), 0, packageId, 1, "stage-1", 1,
                        SituationAction.none(), List.of(), List.of(), List.of(), null, List.of(fact)));

        PremiseInvalidationProposal proposal = new PremiseInvalidationProposal(UUID.randomUUID(), state.version(), packageId,
                1, "stage-2", "the heir is no longer an enemy", Set.of(fact.factId()));
        PremiseInvalidationResult result = PremiseInvalidationPolicy.verify(state, backbone, proposal);

        assertThat(result.accepted()).isTrue();
        assertThat(result.audit().reason()).isEqualTo(PremiseInvalidationAudit.Reason.ACCEPTED);
        StoryRuntimeState audited = state.recordPremiseInvalidation(result);
        assertThat(audited.premiseInvalidationAudits()).containsExactly(result.audit());
        assertThat(audited.recordPremiseInvalidation(result)).isSameAs(audited);
    }

    @Test
    void rejects_expected_path_deviation_and_current_stage_invalidation_with_decisive_reason() {
        StageBackbone backbone = backbone();
        StoryRuntimeState state = StoryRuntimeState.start(stage("stage-1", 1));
        PremiseInvalidationProposal noFact = new PremiseInvalidationProposal(UUID.randomUUID(), 0, packageId,
                1, "stage-2", "the players took another path", Set.of());

        PremiseInvalidationResult rejected = PremiseInvalidationPolicy.verify(state, backbone, noFact);
        assertThat(rejected.accepted()).isFalse();
        assertThat(rejected.audit().reason()).isEqualTo(PremiseInvalidationAudit.Reason.NO_ACCEPTED_FACT);

        PremiseInvalidationProposal current = new PremiseInvalidationProposal(UUID.randomUUID(), 0, packageId,
                1, "stage-1", "the current premise changed", Set.of());
        assertThat(PremiseInvalidationPolicy.verify(state, backbone, current).audit().reason())
                .isEqualTo(PremiseInvalidationAudit.Reason.TARGET_STAGE_ALREADY_STARTED);

        PremiseInvalidationProposal missingReason = new PremiseInvalidationProposal(UUID.randomUUID(), 0, packageId,
                1, "stage-2", "", Set.of());
        assertThat(PremiseInvalidationPolicy.verify(state, backbone, missingReason).audit().reason())
                .isEqualTo(PremiseInvalidationAudit.Reason.INVALIDATION_REASON_MISSING);
    }

    @Test
    void replaces_only_future_suffix_and_keeps_completed_prefix_and_revision_lineage() {
        StageBackbone original = backbone();
        StageBackbone replanned = FutureSuffixReplanningPolicy.replaceFrom(original, "stage-2", List.of(
                new StageBackboneEntry("stage-2b", 99, "replanned", "Recover the heir", "The heir is safe", List.of(evidence)),
                new StageBackboneEntry("stage-3b", 100, "finale", "Face the threat", "Reach the finale", List.of(evidence))));

        assertThat(replanned.revision()).isEqualTo(2);
        assertThat(replanned.stages()).extracting(StageBackboneEntry::stageId)
                .containsExactly("stage-1", "stage-2b", "stage-3b");
        assertThat(replanned.stages()).extracting(StageBackboneEntry::order).containsExactly(1, 2, 3);
        assertThat(original.revision()).isEqualTo(1);
        assertThat(original.stages()).extracting(StageBackboneEntry::stageId)
                .containsExactly("stage-1", "stage-2", "stage-3");
    }

    @Test
    void stale_replan_is_rejected_before_generation() {
        StageBackbone backbone = backbone();
        StoryRuntimeState state = StoryRuntimeState.start(stage("stage-1", 1));
        PremiseInvalidationProposal stale = new PremiseInvalidationProposal(UUID.randomUUID(), 4, packageId,
                backbone.revision(), "stage-2", "a fact invalidates the premise", Set.of());

        assertThatThrownBy(() -> PremiseInvalidationPolicy.verifyOrThrow(state, backbone, stale))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("version");
    }

    private DetailedStage stage(String id, long backboneRevision) {
        return new DetailedStage(packageId, backboneRevision, id, 1, "Find the heir",
                List.of(new RevelationDefinition(id + "-truth", true, List.of(evidence))),
                new ThreatDefinition("The hunters are close", List.of(evidence)),
                new PressureDefinition("The search grows dangerous", List.of(evidence)),
                new FunnelDefinition("The next location is known", List.of(id + "-truth"), List.of(evidence)),
                List.of(new SituationDefinition(id + "-opening", List.of(StageIntent.REVELATION), List.of(id + "-truth"),
                        List.of(), List.of(), List.of(), List.of(), true, List.of(evidence))),
                List.of(), List.of(evidence));
    }

    private StageBackbone backbone() {
        return new StageBackbone(packageId, 1, List.of(
                new StageBackboneEntry("stage-1", 1, "opening", "Find the heir", "Learn the location", List.of(evidence)),
                new StageBackboneEntry("stage-2", 2, "confrontation", "Confront the hunters", "The hunters are faced", List.of(evidence)),
                new StageBackboneEntry("stage-3", 3, "finale", "Face the threat", "Reach the finale", List.of(evidence))), List.of(evidence));
    }
}
