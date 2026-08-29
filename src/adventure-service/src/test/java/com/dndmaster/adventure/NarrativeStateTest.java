package com.dndmaster.adventure;

import com.dndmaster.adventure.domain.runtime.narrative.Belief;
import com.dndmaster.adventure.domain.runtime.narrative.NarrativeContext;
import com.dndmaster.adventure.domain.runtime.narrative.NarrativeState;
import com.dndmaster.adventure.domain.runtime.narrative.StateDelta;
import com.dndmaster.adventure.domain.runtime.narrative.StateDeltaValidator;
import com.dndmaster.adventure.domain.runtime.narrative.WorldFact;
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
}
