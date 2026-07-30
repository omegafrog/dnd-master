package com.dndmaster.aigamemaster.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import java.util.UUID;
import com.dndmaster.aigamemaster.infrastructure.ai.CharacterTagCompletionPort;
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
        assertTrue(prompt.startsWith("Extract only source-grounded"));
        assertTrue(prompt.length() < 2500);
        assertTrue(!prompt.contains("Task-specific instruction:"));
        assertTrue(prompt.contains("optionDetails"));
    }

    @Test
    void addsTaskInstructionOnlyForFocusedFollowUpRequests() {
        var excerpt = new CharacterInputTagController.Excerpt(UUID.randomUUID(), 2, "page 1", "source");
        var prompt = CharacterInputTagController.buildPrompt(new CharacterInputTagController.Request(
                "operation", List.of(excerpt), "character-input-tag-v1", "character-input-tag-prompt-v1",
                "Refine only field 'race'."));

        assertTrue(prompt.contains("Task-specific instruction: Refine only field 'race'."));
        assertTrue(prompt.contains("optionDetails"));
    }

    @Test
    void keepsOnlySourceGroundedDynamicTagsAndDropsMalformedCandidates() {
        var controller = new CharacterInputTagController((CharacterTagCompletionPort) null, new ObjectMapper());
        var result = controller.parseModel("```json\n[{\"key\":\"culture.alignment\",\"label\":\"Alignment\",\"parentKey\":\"culture\",\"required\":false,\"inputMode\":\"SINGLE_SELECT\",\"options\":[\"Neutral\"],\"optionDetails\":[{\"value\":\"Neutral\",\"description\":\"A neutral alignment.\",\"sourceQuote\":\"Alignment: Neutral\",\"evidence\":[{\"documentId\":\"00000000-0000-0000-0000-000000000001\",\"extractionVersion\":1,\"locator\":\"page:2\"}]}],\"confidence\":\"HIGH\",\"sourceQuote\":\"Alignment: Neutral\",\"evidence\":[{\"documentId\":\"00000000-0000-0000-0000-000000000001\",\"extractionVersion\":1,\"locator\":\"page:2\"}],\"sourceType\":\"STORYBOOK\"},{\"key\":\"invented\",\"inputMode\":\"FREE_TEXT\",\"options\":[\"not allowed\"]}]\n```");

        assertEquals(1, result.size());
        assertEquals("culture.alignment", result.getFirst().key());
        assertEquals("STORYBOOK", result.getFirst().sourceType());
        assertTrue(result.getFirst().evidence().size() == 1);
    }

    @Test
    void preservesSelectableOptionInformationAndItsEvidence() {
        var controller = new CharacterInputTagController((CharacterTagCompletionPort) null, new ObjectMapper());
        var result = controller.parseModel("[{\"key\":\"race\",\"label\":\"Race\",\"required\":true,\"inputMode\":\"SINGLE_SELECT\",\"options\":[\"Elf\"],\"optionDetails\":[{\"value\":\"Elf\",\"label\":\"Elf\",\"description\":\"An elf character option.\",\"sourceQuote\":\"Choose an elf.\",\"evidence\":[{\"documentId\":\"00000000-0000-0000-0000-000000000001\",\"extractionVersion\":1,\"locator\":\"page:2\"}]}],\"confidence\":\"HIGH\",\"sourceQuote\":\"Choose a race.\",\"evidence\":[{\"documentId\":\"00000000-0000-0000-0000-000000000001\",\"extractionVersion\":1,\"locator\":\"page:2\"}],\"sourceType\":\"RULEBOOK\"}]");

        assertEquals(1, result.size());
        assertEquals("Elf", result.getFirst().optionDetails().getFirst().value());
        assertEquals("An elf character option.", result.getFirst().optionDetails().getFirst().description());
        assertEquals(1, result.getFirst().optionDetails().getFirst().evidence().size());
    }

    @Test
    void completesMissingSelectableOptionDetailsFromTheFieldEvidence() {
        var controller = new CharacterInputTagController((CharacterTagCompletionPort) null, new ObjectMapper());
        var result = controller.parseModel("operation", "[{\"key\":\"race\",\"label\":\"Race\",\"required\":true,\"inputMode\":\"SINGLE_SELECT\",\"options\":[\"Elf\"],\"confidence\":\"HIGH\",\"sourceQuote\":\"Elf is available.\",\"evidence\":[{\"documentId\":\"00000000-0000-0000-0000-000000000001\",\"extractionVersion\":1,\"locator\":\"page:2\"}],\"sourceType\":\"RULEBOOK\"}]", true);

        assertEquals(1, result.size());
        assertEquals("Elf", result.getFirst().optionDetails().getFirst().value());
        assertEquals(1, result.getFirst().optionDetails().getFirst().evidence().size());
    }

    @Test
    void suppliesTheFieldQuoteForOptionEvidenceWhenTheModelOmitsAnOptionQuote() {
        UUID documentId = UUID.randomUUID();
        var controller = new CharacterInputTagController((operationId, prompt) ->
                "[{\"key\":\"race\",\"options\":[\"Elf\"],\"sourceQuote\":\"Choose an Elf.\",\"optionDetails\":[{\"value\":\"Elf\",\"evidence\":[]}]}]",
                new ObjectMapper());

        var result = controller.extract(new CharacterInputTagController.Request("operation", List.of(
                new CharacterInputTagController.Excerpt(documentId, 12, "page 1", "Choose an Elf.")),
                "character-input-tag-v1", "character-input-tag-prompt-v1"));

        assertEquals("Choose an Elf.", result.candidates().getFirst().optionDetails().getFirst().sourceQuote());
        assertEquals(1, result.candidates().getFirst().optionDetails().getFirst().evidence().size());
    }

    @Test
    void usesTheGroundedOptionValueWhenNeitherTheOptionNorFieldHasAQuote() {
        UUID documentId = UUID.randomUUID();
        var controller = new CharacterInputTagController((operationId, prompt) ->
                "[{\"key\":\"race\",\"options\":[\"Elf\"],\"optionDetails\":[{\"value\":\"Elf\",\"evidence\":[]}]}]",
                new ObjectMapper());

        var result = controller.extract(new CharacterInputTagController.Request("operation", List.of(
                new CharacterInputTagController.Excerpt(documentId, 12, "page 1", "Choose an Elf.")),
                "character-input-tag-v1", "character-input-tag-prompt-v1"));

        assertEquals("Elf", result.candidates().getFirst().optionDetails().getFirst().sourceQuote());
        assertEquals("Elf", result.candidates().getFirst().sourceQuote());
    }

    @Test
    void infersSelectionModeAndEvidenceForACompactModelResponse() {
        UUID documentId = UUID.randomUUID();
        var controller = new CharacterInputTagController((operationId, prompt) ->
                "[{\"key\":\"halfling_subrace\",\"options\":[\"라이트풋\",\"스타우트\"]}]", new ObjectMapper());
        var result = controller.extract(new CharacterInputTagController.Request("operation", List.of(
                new CharacterInputTagController.Excerpt(documentId, 12, "page 1",
                        "하플링은 라이트풋과 스타우트라는 두 하위종족으로 나뉘어 있습니다.")),
                "character-input-tag-v1", "character-input-tag-prompt-v1"));

        assertEquals(1, result.candidates().size());
        assertEquals("SINGLE_SELECT", result.candidates().getFirst().inputMode());
        assertEquals(List.of("라이트풋", "스타우트"), result.candidates().getFirst().options());
        assertEquals(documentId, result.candidates().getFirst().evidence().getFirst().documentId());
        assertEquals(2, result.candidates().getFirst().optionDetails().size());
    }

    @Test
    void recoversQwenStyleNestedOptionsIntoAGroundedReviewDraft() {
        UUID documentId = UUID.randomUUID();
        var controller = new CharacterInputTagController((operationId, prompt) ->
                "{\"inputMode\":\"text\",\"label\":\"종족\",\"confidence\":0.95,\"sourceType\":\"document\",\"optionDetails\":{\"options\":[\"라이트풋\",\"스타우트\"]}}", new ObjectMapper());
        var result = controller.extract(new CharacterInputTagController.Request("operation", List.of(
                new CharacterInputTagController.Excerpt(documentId, 12, "page 1",
                        "하플링은 라이트풋과 스타우트라는 두 하위종족으로 나뉘어 있습니다.")),
                "character-input-tag-v1", "character-input-tag-prompt-v1"));

        assertEquals(1, result.candidates().size());
        assertEquals("종족", result.candidates().getFirst().key());
        assertEquals("SINGLE_SELECT", result.candidates().getFirst().inputMode());
        assertEquals("HIGH", result.candidates().getFirst().confidence());
        assertEquals("RULEBOOK", result.candidates().getFirst().sourceType());
    }

    @Test
    void deduplicatesRepeatedModelOptions() {
        var controller = new CharacterInputTagController((CharacterTagCompletionPort) null, new ObjectMapper());

        var result = controller.parseModel("[{\"key\":\"race\",\"options\":[\"Elf\",\"Elf\",\"Dwarf\"]}]");

        assertEquals(List.of("Elf", "Dwarf"), result.getFirst().options());
    }

    @Test
    void removesTheFieldHeadingWhenItIsMistakenForASelectableValue() {
        UUID documentId = UUID.randomUUID();
        var controller = new CharacterInputTagController((operationId, prompt) ->
                "[{\"key\":\"race\",\"label\":\"종족\",\"options\":[\"종족\",\"엘프\"]}]", new ObjectMapper());

        var result = controller.extract(new CharacterInputTagController.Request("operation", List.of(
                new CharacterInputTagController.Excerpt(documentId, 1, "page 1", "종족 선택: 엘프")), "v1", "p1"));

        assertEquals(List.of("엘프"), result.candidates().getFirst().options());
    }
}
