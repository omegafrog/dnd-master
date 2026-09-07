package com.dndmaster.combatmap.application.view;

import com.dndmaster.combatmap.domain.GridSpec;

public final class FallbackGridPolicy {
    private final int targetCells;
    public FallbackGridPolicy(int targetCells) {
        if (targetCells < 1) throw new IllegalArgumentException("target cells must be positive");
        this.targetCells = targetCells;
    }
    public GridCalibration create(MapContentBounds bounds) {
        int cell = Math.max(1, Math.round(Math.min(bounds.width(), bounds.height()) / (float) targetCells));
        int width = Math.max(1, bounds.width() / cell), height = Math.max(1, bounds.height() / cell);
        return new GridCalibration(new GridSpec(width, height, cell, 5), 0, 0, bounds, GridSource.FALLBACK, 1.0);
    }
}
