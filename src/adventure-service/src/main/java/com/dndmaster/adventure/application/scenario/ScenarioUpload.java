package com.dndmaster.adventure.application.scenario;

import com.dndmaster.adventure.domain.scenario.OwnerPlayerId;
import java.util.Objects;

public record ScenarioUpload(OwnerPlayerId ownerPlayerId, String originalFilename, byte[] content) {
    public ScenarioUpload {
        Objects.requireNonNull(ownerPlayerId, "owner player id must not be null");
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException("original filename must not be blank");
        }
        originalFilename = originalFilename.trim();
        Objects.requireNonNull(content, "content must not be null");
        if (content.length == 0) {
            throw new IllegalArgumentException("scenario content must not be empty");
        }
        content = content.clone();
    }

    @Override
    public byte[] content() {
        return content.clone();
    }
}
