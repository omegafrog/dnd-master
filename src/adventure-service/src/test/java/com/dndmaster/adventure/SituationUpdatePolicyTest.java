package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.dndmaster.adventure.application.runtime.SituationUpdatePolicy;
import com.dndmaster.adventure.application.runtime.SituationUpdateProposal;
import com.dndmaster.adventure.domain.runtime.CurrentSituation;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SituationUpdatePolicyTest {
    @Test
    void transition_preserves_current_values_for_omitted_situation_elements() {
        CurrentSituation current = new CurrentSituation(UUID.randomUUID(), 1,
                "cellar", "find the source", "rats are nearby", "secure the room");

        CurrentSituation updated = SituationUpdatePolicy.apply(current,
                SituationUpdateProposal.transition(null, "blocked by the rat swarm", null, null));

        assertNotEquals(current.situationId(), updated.situationId());
        assertEquals("cellar", updated.location());
        assertEquals("blocked by the rat swarm", updated.problem());
        assertEquals("rats are nearby", updated.threat());
        assertEquals("secure the room", updated.goal());
    }
}
