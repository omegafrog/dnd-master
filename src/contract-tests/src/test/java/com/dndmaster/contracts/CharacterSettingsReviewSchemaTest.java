package com.dndmaster.contracts;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

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

        JsonNode valid = JSON.readTree("""
                {"baseSchema":{"edition":"DND_5E_2014","fields":[]},
                 "storybookProposals":[{"proposalId":"proposal-1","key":"race","label":"Race","description":"Elf only",
                   "sourceDocument":{"knowledgeDocumentId":"doc-1","originalFilename":"story.pdf","extractionVersion":3},
                   "sourceQuote":"Only elves.","evidence":[{"locator":"page:4","excerpt":"Only elves."}],
                   "decisionState":"UNDECIDED","readinessState":"READY"}],
                 "storybookExtractionState":"PROPOSALS_AVAILABLE"}
                """);
        JsonNode invalid = JSON.readTree("""
                {"baseSchema":{"edition":"DND_5E_2014","fields":[]},
                 "storybookProposals":[{"proposalId":"","key":"race","label":"Race","description":"Elf only",
                   "sourceDocument":null,"sourceQuote":"","evidence":[],
                   "decisionState":"USE","readinessState":"READY"}],
                 "storybookExtractionState":"UNKNOWN"}
                """);
        assertTrue(isValidReviewPayload(schema, valid));
        assertFalse(isValidReviewPayload(schema, invalid));
    }

    private static boolean isValidReviewPayload(JsonNode schema, JsonNode payload) {
        if (!payload.isObject() || !hasRequired(schema, payload)) return false;
        JsonNode base = payload.get("baseSchema");
        if (!base.isObject() || !hasRequired(schema.at("/properties/baseSchema"), base)
                || !base.get("edition").isTextual() || !base.get("fields").isArray()) return false;
        JsonNode proposals = payload.get("storybookProposals");
        if (!proposals.isArray()) return false;
        JsonNode proposalSchema = schema.at("/properties/storybookProposals/items");
        for (JsonNode proposal : proposals) {
            if (!proposal.isObject() || !hasRequired(proposalSchema, proposal)
                    || proposal.get("proposalId").asText().isBlank()
                    || !proposal.get("sourceQuote").isTextual()
                    || !proposal.get("evidence").isArray()
                    || !isEnum(proposalSchema.at("/properties/decisionState"), proposal.get("decisionState"))
                    || !isEnum(proposalSchema.at("/properties/readinessState"), proposal.get("readinessState"))) return false;
            if (!proposal.get("sourceDocument").isNull() && !proposal.get("sourceDocument").isObject()) return false;
        }
        return isEnum(schema.at("/properties/storybookExtractionState"), payload.get("storybookExtractionState"));
    }

    private static boolean hasRequired(JsonNode schema, JsonNode payload) {
        for (JsonNode required : schema.get("required")) if (!payload.has(required.asText())) return false;
        return true;
    }

    private static boolean isEnum(JsonNode schema, JsonNode value) {
        for (JsonNode allowed : schema.get("enum")) if (allowed.equals(value)) return true;
        return false;
    }
}
