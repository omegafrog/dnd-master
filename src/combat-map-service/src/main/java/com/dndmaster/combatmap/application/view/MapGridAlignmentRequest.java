package com.dndmaster.combatmap.application.view;

import java.util.Objects;
import java.util.UUID;

/** 정렬 저장 요청. 작업 식별자는 결과가 불명확한 저장을 안전하게 다시 요청하는 데 쓴다. */
public record MapGridAlignmentRequest(UUID commandId, long expectedVersion, String imageRevision,
                                      double originX, double originY, double cellSize) {
    public MapGridAlignmentRequest {
        Objects.requireNonNull(commandId, "command id is required");
        if (expectedVersion < 0 || imageRevision == null || imageRevision.isBlank() || !Double.isFinite(originX)
                || !Double.isFinite(originY) || !Double.isFinite(cellSize) || cellSize <= 0) {
            throw new IllegalArgumentException("invalid map grid alignment request");
        }
    }
}
