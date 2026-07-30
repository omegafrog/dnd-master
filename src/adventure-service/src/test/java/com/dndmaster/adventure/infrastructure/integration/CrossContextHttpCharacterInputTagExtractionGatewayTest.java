package com.dndmaster.adventure.infrastructure.integration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.application.scenario.blueprint.CharacterInputTagExtractionPort;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.domain.scenario.InputMode;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceReference;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class CrossContextHttpCharacterInputTagExtractionGatewayTest {
    @Test
    void groundsByExactSourceReferenceWithoutComparingModelQuoteToExcerptText() {
        UUID documentId = UUID.randomUUID();
        KnowledgeDocumentId knowledgeDocumentId = new KnowledgeDocumentId(documentId);
        var candidate = new CharacterInputTagExtractionPort.CharacterInputTagCandidate(
                "race", "Race", null, true, InputMode.SINGLE_SELECT, List.of("Dwarf"), List.of(), "HIGH",
                List.of(new ScenarioSourceReference(knowledgeDocumentId, 12, "offset 1-2")),
                "model-generated summary that is not copied verbatim", "RULEBOOK");
        var excerpt = new CharacterInputTagExtractionPort.SourceExcerpt(
                knowledgeDocumentId, 12, "offset 1-2", "indexed excerpt");

        assertTrue(CrossContextHttpCharacterInputTagExtractionGateway.grounded(candidate, List.of(excerpt)));
    }
}
