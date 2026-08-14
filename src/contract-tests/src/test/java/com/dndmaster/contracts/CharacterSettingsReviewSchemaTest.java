package com.dndmaster.contracts;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
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

        JsonNode valid = JSON.readTree("""
                {"baseSchema":{"edition":"DND_5E_2014","fields":[{"key":"race","options":["Elf"],"required":true,"sourceType":"TEMPLATE","inputStatus":"EXTRACTED","diagnostics":[],"inputMode":"SINGLE_SELECT","suggestions":[],"sourceQuote":"","evidence":[],"optionDetails":[],"label":"Race"}]},
                 "storybookProposals":[{"proposalId":"proposal-1","key":"race","label":"Race","description":"Elf only",
                   "sourceDocument":{"knowledgeDocumentId":"doc-1","originalFilename":"story.pdf","extractionVersion":3},
                   "sourceQuote":"Only elves.","evidence":[{"locator":"page:4","excerpt":"Only elves."}],
                   "decisionState":"UNDECIDED","readinessState":"READY"}],
                 "appliedSettingsSummary":{"baseSchemaIncluded":true,"appliedProposalIds":[],"excludedProposalIds":[],"unresolvedProposalCount":1},
                 "storybookExtractionState":"PROPOSALS_AVAILABLE"}
                """);
        JsonNode invalid = JSON.readTree("""
                {"baseSchema":{"edition":"DND_5E_2014","fields":[]},
                 "storybookProposals":[{"proposalId":"","key":"race","label":"Race","description":"Elf only",
                   "sourceDocument":{"knowledgeDocumentId":"doc-1"},"sourceQuote":"","evidence":[{"locator":3,"excerpt":""}],
                   "decisionState":"USE","readinessState":"READY"}],
                 "storybookExtractionState":"UNKNOWN"}
                """);
        JsonSchema validator = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(schema);
        assertTrue(validator.validate(valid).isEmpty());
        assertFalse(validator.validate(invalid).isEmpty());
    }
}
