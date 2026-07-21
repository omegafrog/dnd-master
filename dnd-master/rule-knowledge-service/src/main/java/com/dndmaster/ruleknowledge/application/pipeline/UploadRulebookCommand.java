package com.dndmaster.ruleknowledge.application.pipeline;

import com.dndmaster.ruleknowledge.domain.rulebook.OwnerPlayerId;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookFormat;
import java.util.Objects;
import java.util.UUID;

public record UploadRulebookCommand(
        String operationKey,
        OwnerPlayerId ownerPlayerId,
        RulebookFormat format,
        byte[] fileContent) {

    public UploadRulebookCommand {
        Objects.requireNonNull(ownerPlayerId, "ownerPlayerId must not be null");
        Objects.requireNonNull(format, "format must not be null");
        Objects.requireNonNull(fileContent, "fileContent must not be null");
        if (fileContent.length == 0) {
            throw new IllegalArgumentException("fileContent must not be empty");
        }
        operationKey = (operationKey == null || operationKey.isBlank())
                ? UUID.randomUUID().toString()
                : operationKey.trim();
    }
}
