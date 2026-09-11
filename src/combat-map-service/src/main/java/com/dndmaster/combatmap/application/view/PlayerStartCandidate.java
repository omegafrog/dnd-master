package com.dndmaster.combatmap.application.view;

import com.dndmaster.combatmap.domain.GridPosition;
import java.util.List;
import java.util.Objects;

/** 지도 준비 단계에서 검토할 플레이어 시작 칸 제안. */
public record PlayerStartCandidate(GridPosition position, double confidence, List<String> evidence,
        String source) {
    public PlayerStartCandidate {
        position = Objects.requireNonNull(position, "player start position must not be null");
        if (!Double.isFinite(confidence) || confidence < 0 || confidence > 1) {
            throw new IllegalArgumentException("player start confidence must be between 0 and 1");
        }
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        source = source == null || source.isBlank() ? "SCENARIO_ENTRY" : source.trim();
    }
}
