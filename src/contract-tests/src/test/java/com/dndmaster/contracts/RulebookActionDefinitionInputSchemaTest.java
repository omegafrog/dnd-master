package com.dndmaster.contracts;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RulebookActionDefinitionInputSchemaTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void request_contract_accepts_sixty_five_evidence_items_and_candidates_per_query() throws IOException {
        Path workingDirectory = Path.of(System.getProperty("user.dir"));
        Path contract = Files.isRegularFile(workingDirectory.resolve("contracts/rule-knowledge/schemas/rulebook-action-definition-agent-input.json"))
                ? workingDirectory.resolve("contracts/rule-knowledge/schemas/rulebook-action-definition-agent-input.json")
                : workingDirectory.resolve("../contracts/rule-knowledge/schemas/rulebook-action-definition-agent-input.json").normalize();
        var schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
                .getSchema(JSON.readTree(Files.readString(contract)));
        var input = JSON.createObjectNode().put("documentId", UUID.randomUUID().toString()).put("extractionVersion", "v1");
        input.putArray("queryCoverage").addObject().put("queryId", "assigned-actions")
                .put("queryFingerprint", "fingerprint").put("candidateCount", 64);
        var evidence = input.putArray("evidence");
        var evidenceIds = JSON.createArrayNode();
        for (int index = 0; index < 65; index++) {
            String id = UUID.nameUUIDFromBytes(("evidence-" + index).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
            evidenceIds.add(id);
            evidence.addObject().put("id", id).put("locator", "page=" + index)
                    .putArray("anchors").addObject().put("id", "0").put("text", "source " + index);
        }
        var action = input.putArray("assignedActions").addObject().put("id", UUID.randomUUID().toString())
                .put("name", "Action").put("actionKind", "SPELL").put("sourceLocator", "page=1");
        action.set("evidenceIds", evidenceIds);
        action.putObject("knownFields"); action.putArray("neededFields"); action.putArray("sharedRules");

        assertTrue(schema.validate(input).isEmpty());
    }
}
