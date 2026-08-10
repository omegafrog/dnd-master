package com.dndmaster.ruleknowledge.domain.evidence;

import com.dndmaster.ruleknowledge.domain.rulebook.SourceSpan;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record EvidenceEdge(
        UUID from,
        UUID to,
        EvidenceEdgeType type,
        List<SourceSpan> sourceSpans) {
    public EvidenceEdge {
        Objects.requireNonNull(from, "from must not be null");
        Objects.requireNonNull(to, "to must not be null");
        if (from.equals(to)) throw new IllegalArgumentException("edge cannot point to itself");
        Objects.requireNonNull(type, "type must not be null");
        sourceSpans = List.copyOf(Objects.requireNonNull(sourceSpans, "sourceSpans must not be null"));
        if (sourceSpans.isEmpty()) throw new IllegalArgumentException("sourceSpans must not be empty");
    }
}
