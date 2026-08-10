package com.dndmaster.ruleknowledge.domain.evidence;

import com.dndmaster.ruleknowledge.domain.rulebook.RulebookId;
import com.dndmaster.ruleknowledge.domain.rulebook.SourceSpan;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record EvidenceUnit(
        UUID id,
        RulebookId documentId,
        long extractionVersion,
        EvidenceKind kind,
        String content,
        EvidenceVisibility visibility,
        List<SourceSpan> sourceSpans) {
    public EvidenceUnit {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(documentId, "documentId must not be null");
        if (extractionVersion <= 0) throw new IllegalArgumentException("extractionVersion must be positive");
        Objects.requireNonNull(kind, "kind must not be null");
        if (content == null || content.isBlank()) throw new IllegalArgumentException("content must not be blank");
        Objects.requireNonNull(visibility, "visibility must not be null");
        sourceSpans = List.copyOf(Objects.requireNonNull(sourceSpans, "sourceSpans must not be null"));
        if (sourceSpans.isEmpty()) throw new IllegalArgumentException("sourceSpans must not be empty");
    }

    public int tokenCount() {
        return content.trim().split("\\s+").length;
    }

    public boolean canExposeToPlayer() {
        return visibility.canExposeToPlayer();
    }
}
