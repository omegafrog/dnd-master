package com.dndmaster.ruleknowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dndmaster.ruleknowledge.domain.extraction.ContentRole;
import com.dndmaster.ruleknowledge.domain.extraction.ContentRoleClassificationPolicy;
import com.dndmaster.ruleknowledge.domain.extraction.DocumentNode;
import com.dndmaster.ruleknowledge.domain.extraction.DocumentNodeType;
import java.util.List;
import org.junit.jupiter.api.Test;

class ContentRoleClassificationPolicyTest {
    private final ContentRoleClassificationPolicy policy = new ContentRoleClassificationPolicy();

    @Test
    void classifiesKnownHeadingsAndKeepsStorybookMultiRole() {
        DocumentNode node = DocumentNode.heading("story", 1, "Adventure map and monster rules");

        var result = policy.classify(node, "STORYBOOK");

        assertEquals(List.of(ContentRole.KNOWLEDGE, ContentRole.GAME_ASSET, ContentRole.GM_MATERIAL), result.roles());
        assertEquals(List.of(), result.warnings());
    }

    @Test
    void ambiguousStructureDowngradesToRawWithWarning() {
        DocumentNode node = new DocumentNode("raw", DocumentNodeType.UNKNOWN, 1, null, "mystery", List.of(), List.of());

        var result = policy.classify(node, "RULEBOOK");

        assertEquals(List.of(ContentRole.RAW), result.roles());
        assertEquals(1, result.warnings().size());
        assertEquals("AMBIGUOUS_STRUCTURE", result.warnings().get(0).code());
    }
}
