package com.dndmaster.ruleknowledge.application.pipeline;

import com.dndmaster.ruleknowledge.domain.rulebook.OwnerPlayerId;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookFormat;
import com.dndmaster.ruleknowledge.domain.rulebook.DocumentType;
import java.util.Objects;
import java.util.UUID;

public record UploadRulebookCommand(
        String operationKey,
        OwnerPlayerId ownerPlayerId,
        DocumentType documentType,
        RulebookFormat format,
        byte[] fileContent,
        String originalFilename) {

    public UploadRulebookCommand(
            String operationKey,
            OwnerPlayerId ownerPlayerId,
            DocumentType documentType,
            RulebookFormat format,
            byte[] fileContent) {
        this(operationKey, ownerPlayerId, documentType, format, fileContent, "legacy-rulebook");
    }

    public UploadRulebookCommand {
        Objects.requireNonNull(ownerPlayerId, "ownerPlayerId must not be null");
        documentType = DocumentType.require(documentType);
        Objects.requireNonNull(format, "format must not be null");
        Objects.requireNonNull(fileContent, "fileContent must not be null");
        if (fileContent.length == 0) {
            throw new IllegalArgumentException("fileContent must not be empty");
        }
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException("original filename must not be blank");
        }
        originalFilename = originalFilename.trim();
        operationKey = (operationKey == null || operationKey.isBlank())
                ? UUID.randomUUID().toString()
                : operationKey.trim();
    }
}
