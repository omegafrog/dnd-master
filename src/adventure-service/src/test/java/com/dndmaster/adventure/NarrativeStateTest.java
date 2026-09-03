package com.dndmaster.adventure;

import com.dndmaster.adventure.domain.runtime.narrative.Belief;
import com.dndmaster.adventure.domain.runtime.narrative.NarrativeContext;
import com.dndmaster.adventure.domain.runtime.narrative.NarrativeState;
import com.dndmaster.adventure.domain.runtime.narrative.StateDelta;
import com.dndmaster.adventure.domain.runtime.narrative.StateDeltaValidator;
import com.dndmaster.adventure.domain.runtime.narrative.WorldFact;
import com.dndmaster.adventure.domain.runtime.narrative.FactAuthority;
import java.util.List;
import java.util.Set;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NarrativeStateTest {
    private static final String PLAYER = "player";
    private static final String ALICE = "alice";
    private static final String BOB = "bob";

    @Test
    void projectsOnlyFactsKnownByTheRequestedActor() {
        NarrativeState state = NarrativeState.empty()
                .addWorldFact(new WorldFact("secret", "The vault is occupied", false))
                .addWorldFact(new WorldFact("rumor", "The bell will ring", false))
                .recordKnowledge(ALICE, "secret")
                .recordKnowledge(BOB, "rumor")
                .revealFact("rumor", 1, "player action");

        NarrativeContext context = state.project(BOB, "A cold corridor");

        assertThat(context.factsKnownBy()).containsExactly("rumor");
        assertThat(context.worldFacts()).extracting(WorldFact::id).containsExactly("rumor");
        assertThat(context.worldFacts()).extracting(WorldFact::id).doesNotContain("secret");
        assertThat(state.project(PLAYER, "A cold corridor").factsKnownBy()).containsExactly("rumor");
    }

    @Test
    void revealIsMonotonicAndBeliefDoesNotChangeWorldFact() {
        NarrativeState state = NarrativeState.empty()
                .addWorldFact(new WorldFact("door", "The door is locked", false))
                .recordBelief(new Belief(ALICE, "door", "The door is open", 0.7, "observation"))
                .revealFact("door", 4, "test");

        assertThat(state.revealedFacts()).containsKey("door");
        assertThat(state.worldFacts().get("door").value()).isEqualTo("The door is locked");
        assertThatThrownBy(() -> state.hideFact("door")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void validatedDeltaCommitsWithOptimisticVersion() {
        NarrativeState state = NarrativeState.empty()
                .addWorldFact(new WorldFact("door", "The door is locked", false));
        StateDelta delta = new StateDelta(0, Set.of("door"), Set.of("door"),
                List.of(), List.of(), List.of(), List.of(), List.of());

        NarrativeState committed = new StateDeltaValidator().validateAndCommit(state, delta);

        assertThat(committed.version()).isEqualTo(1);
        assertThat(committed.revealedFacts()).containsKey("door");
        assertThatThrownBy(() -> new StateDeltaValidator().validateAndCommit(committed, delta))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("version");
    }

    @Test
    void invalidDeltaCannotCommitUnknownOrUnrevealedActorFacts() {
        NarrativeState state = NarrativeState.empty();
        StateDelta delta = new StateDelta(0, Set.of("missing"), Set.of(), List.of(), List.of(), List.of(), List.of(), List.of());

        assertThatThrownBy(() -> new StateDeltaValidator().validateAndCommit(state, delta))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown");
        assertThat(state.version()).isZero();
    }

    @Test
    void narrativeStateRoundTripsThroughRuntimeJsonWithoutLosingReveal() throws Exception {
        NarrativeState state = NarrativeState.empty().addWorldFact(new WorldFact("fact", "public", false)).revealFact("fact", 1, "test");
        NarrativeState restored = new ObjectMapper().findAndRegisterModules().readValue(
                new ObjectMapper().findAndRegisterModules().writeValueAsString(state), NarrativeState.class);

        assertThat(restored.version()).isEqualTo(state.version());
        assertThat(restored.revealedFacts()).containsKey("fact");
    }

    @Test
    void legacyWorldFactJsonDefaultsToUnexposedAuthority() throws Exception {
        String legacyJson = """
                {"version":0,"worldFacts":{"fact":{"id":"fact","value":"public","mutable":false}},
                "revealedFacts":{},"characterKnowledge":{},"relationships":[],"activeThreads":[],"recentEvents":[]}
                """;

        NarrativeState restored = new ObjectMapper().findAndRegisterModules().readValue(legacyJson, NarrativeState.class);

        assertThat(restored.worldFacts().get("fact").authority()).isEqualTo(FactAuthority.GENERATED_UNEXPOSED);
    }

    @Test
    void canonicalFactsOverrideEstablishedAndUnexposedFacts() {
        NarrativeState state = NarrativeState.empty()
                .addWorldFact(new WorldFact("gate", "the gate is open", false, FactAuthority.GENERATED_ESTABLISHED))
                .addWorldFact(new WorldFact("secret", "the room is empty", false, FactAuthority.GENERATED_UNEXPOSED));

        NarrativeState resolved = new StateDeltaValidator().validateAndCommitProposal(state,
                StateDelta.proposing(0, List.of(
                        new WorldFact("gate", "the gate is sealed", false, FactAuthority.CANONICAL_SOURCE),
                        new WorldFact("secret", "the room contains a shrine", false, FactAuthority.CANONICAL_SOURCE))));

        assertThat(resolved.worldFacts().get("gate").value()).isEqualTo("the gate is sealed");
        assertThat(resolved.worldFacts().get("secret").value()).isEqualTo("the room contains a shrine");
    }

    @Test
    void establishedFactsArePreservedWhenAProposalConflicts() {
        NarrativeState state = NarrativeState.empty()
                .addWorldFact(new WorldFact("guide", "the guide wears a red cloak", false, FactAuthority.GENERATED_ESTABLISHED));

        NarrativeState resolved = new StateDeltaValidator().validateAndCommitProposal(state,
                StateDelta.proposing(0, List.of(
                        new WorldFact("guide", "the guide wears a blue cloak", false, FactAuthority.GENERATED_ESTABLISHED))));

        assertThat(resolved.worldFacts().get("guide").value()).isEqualTo("the guide wears a red cloak");
    }

    @Test
    void unexposedFactsCanBeCorrectedByAProposal() {
        NarrativeState state = NarrativeState.empty()
                .addWorldFact(new WorldFact("weather", "it is raining", false, FactAuthority.GENERATED_UNEXPOSED));

        NarrativeState resolved = new StateDeltaValidator().validateAndCommitProposal(state,
                StateDelta.proposing(0, List.of(
                        new WorldFact("weather", "it is clear", false, FactAuthority.GENERATED_UNEXPOSED))));

        assertThat(resolved.worldFacts().get("weather").value()).isEqualTo("it is clear");
    }
}
