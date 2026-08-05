package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.adventure.domain.runtime.GmInput;
import com.dndmaster.adventure.domain.runtime.GmTurn;
import com.dndmaster.adventure.application.runtime.GmTurnCommitPolicy;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GmTurnCommitPolicyTest {
    @Test
    void only_committed_turn_with_matching_version_is_publishable() {
        GmTurn started = GmTurn.start(UUID.randomUUID(), UUID.randomUUID(), 4, new GmInput.TextInput("look"));
        assertThrows(IllegalStateException.class, () -> GmTurnCommitPolicy.requirePublishable(started, 5));
        assertDoesNotThrow(() -> GmTurnCommitPolicy.requirePublishable(started.process().commit("provider"), 5));
        assertThrows(IllegalStateException.class, () -> GmTurnCommitPolicy.requirePublishable(started.process().commit("provider"), 4));
    }
}
