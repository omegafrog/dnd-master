package com.dndmaster.ruleknowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.ruleknowledge.application.registration.StoredRulebookRegistration;
import com.dndmaster.ruleknowledge.domain.rulebook.DocumentType;
import com.dndmaster.ruleknowledge.domain.rulebook.KnowledgeDocumentId;
import com.dndmaster.ruleknowledge.domain.rulebook.KnowledgeDocumentMetadata;
import com.dndmaster.ruleknowledge.domain.rulebook.ExtractionStatus;
import com.dndmaster.ruleknowledge.domain.rulebook.OwnerPlayerId;
import com.dndmaster.ruleknowledge.domain.rulebook.ProcessingStatus;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookFormat;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class KnowledgeDocumentCompatibilityTest {
    @Test
    void documentTypeIsRequiredAndOnlySupportsKnownTypes() {
        assertThrows(NullPointerException.class, () -> new KnowledgeDocumentMetadata(
                KnowledgeDocumentId.generate(), owner(), null, "rules.pdf", RulebookFormat.PDF, 1));
        assertEquals(List.of(DocumentType.RULEBOOK, DocumentType.STORYBOOK), List.of(DocumentType.values()));
    }

    @Test
    void knowledgeDocumentIdAndMetadataValidateDocumentIdentity() {
        UUID value = UUID.randomUUID();
        KnowledgeDocumentId id = new KnowledgeDocumentId(value);
        KnowledgeDocumentMetadata metadata = new KnowledgeDocumentMetadata(
                id, owner(), DocumentType.STORYBOOK, "campaign.md", RulebookFormat.TXT, 12);

        assertEquals(value, metadata.id().value());
        assertEquals(DocumentType.STORYBOOK, metadata.documentType());
        assertEquals("campaign.md", metadata.originalFilename());
        assertThrows(IllegalArgumentException.class, () -> new KnowledgeDocumentMetadata(
                id, owner(), DocumentType.STORYBOOK, " ", RulebookFormat.TXT, 12));
        assertThrows(IllegalArgumentException.class, () -> new KnowledgeDocumentMetadata(
                id, owner(), DocumentType.STORYBOOK, "campaign.md", RulebookFormat.TXT, 0));
    }

    @Test
    void legacyRulebookRegistrationIsAUnambiguouslyTypedKnowledgeDocument() {
        RulebookId legacyId = new RulebookId(UUID.randomUUID());
        StoredRulebookRegistration registration = new StoredRulebookRegistration(
                legacyId,
                owner(),
                "legacy-operation",
                "hash",
                RulebookFormat.PDF,
                42,
                "rulebooks/legacy.pdf",
                ProcessingStatus.INDEXED,
                ExtractionStatus.SUCCESS,
                "content",
                List.of(),
                null,
                0,
                Instant.now(),
                Instant.now());

        assertEquals(new KnowledgeDocumentId(legacyId.value()), registration.knowledgeDocumentId());
        assertEquals(DocumentType.RULEBOOK, registration.documentType());
        assertEquals("legacy-rulebook", registration.originalFilename());
    }

    private static OwnerPlayerId owner() {
        return new OwnerPlayerId(UUID.randomUUID());
    }
}
