package com.dndmaster.ruleknowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.ruleknowledge.domain.definition.GameSystemDefinitionRevision;
import com.dndmaster.ruleknowledge.domain.definition.GameSystemDefinitionStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GameSystemDefinitionRevisionTest {
    @Test
    void publication_normalizes_json_and_makes_revision_immutable() {
        var draft = GameSystemDefinitionRevision.draft(UUID.randomUUID(), 2, "{ \"time\": { \"secondsPerTurn\": 6 } }");
        var published = draft.publish();
        assertEquals(GameSystemDefinitionStatus.DRAFT, draft.status());
        assertEquals(GameSystemDefinitionStatus.PUBLISHED, published.status());
        assertEquals("{\"time\":{\"secondsPerTurn\":6}}", published.definitionJson());
        assertThrows(IllegalStateException.class, published::publish);
    }

    @Test
    void malformed_definition_is_rejected() {
        assertThrows(IllegalArgumentException.class,
                () -> GameSystemDefinitionRevision.draft(UUID.randomUUID(), 1, "not-json"));
    }
}
