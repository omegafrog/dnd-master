package com.dndmaster.contracts;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CharacterSettingsReviewSchemaTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void review_contract_separates_base_schema_proposals_and_extraction_states() throws IOException {
        Path workingDirectory = Path.of(System.getProperty("user.dir"));
        Path path = Files.isRegularFile(workingDirectory.resolve("contracts/adventure/schemas/character-settings-review.json"))
                ? workingDirectory.resolve("contracts/adventure/schemas/character-settings-review.json")
                : workingDirectory.resolve("../contracts/adventure/schemas/character-settings-review.json").normalize();
        JsonNode schema = JSON.readTree(Files.readString(path));

        assertTrue(schema.at("/required").toString().contains("baseSchema"));
        assertTrue(schema.at("/required").toString().contains("storybookProposals"));
        assertTrue(schema.at("/required").toString().contains("storybookExtractionState"));
        assertTrue(schema.at("/properties/storybookProposals/items/required").toString().contains("proposalId"));
        assertTrue(schema.at("/properties/storybookProposals/items/required").toString().contains("sourceDocument"));
        assertTrue(schema.at("/properties/storybookProposals/items/required").toString().contains("sourceQuote"));
        assertTrue(schema.at("/properties/storybookProposals/items/required").toString().contains("decisionState"));
        assertTrue(schema.at("/properties/storybookProposals/items/required").toString().contains("readinessState"));
        assertTrue(schema.at("/properties/storybookExtractionState/enum").toString().contains("NO_PROPOSALS"));
        assertTrue(schema.at("/properties/storybookExtractionState/enum").toString().contains("EXTRACTION_FAILED"));
        assertTrue(schema.at("/properties/storybookExtractionState/enum").toString().contains("INSUFFICIENT_EVIDENCE"));
    }
}
