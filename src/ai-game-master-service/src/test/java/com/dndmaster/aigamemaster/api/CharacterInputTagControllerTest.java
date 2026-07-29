package com.dndmaster.aigamemaster.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class CharacterInputTagControllerTest {
    @Test
    void boundsAndLabelsSourceExcerptsBeforeSendingThemToTheModel() {
        var excerpt = new CharacterInputTagController.Excerpt(
                UUID.randomUUID(), 2, "page 1", "x".repeat(4000));
        var prompt = CharacterInputTagController.buildPrompt(new CharacterInputTagController.Request(
                "operation", List.of(excerpt), "character-input-tag-v1", "character-input-tag-prompt-v1"));

        assertTrue(prompt.contains("documentId=" + excerpt.documentId()));
        assertTrue(prompt.contains("locator=page 1"));
        assertTrue(prompt.startsWith("/no_think"));
        assertTrue(prompt.length() < 2500);
    }

    @Test
    void keepsOnlySourceGroundedDynamicTagsAndDropsMalformedCandidates() {
        var controller = new CharacterInputTagController(null, new ObjectMapper());
        var result = controller.parseModel("```json\n[{\"key\":\"culture.alignment\",\"label\":\"Alignment\",\"parentKey\":\"culture\",\"required\":false,\"inputMode\":\"SINGLE_SELECT\",\"options\":[\"Neutral\"],\"confidence\":\"HIGH\",\"sourceQuote\":\"Alignment: Neutral\",\"evidence\":[{\"documentId\":\"00000000-0000-0000-0000-000000000001\",\"extractionVersion\":1,\"locator\":\"page:2\"}],\"sourceType\":\"STORYBOOK\"},{\"key\":\"invented\",\"inputMode\":\"FREE_TEXT\",\"options\":[\"not allowed\"]}]\n```");

        assertEquals(1, result.size());
        assertEquals("culture.alignment", result.getFirst().key());
        assertEquals("STORYBOOK", result.getFirst().sourceType());
        assertTrue(result.getFirst().evidence().size() == 1);
    }
}
