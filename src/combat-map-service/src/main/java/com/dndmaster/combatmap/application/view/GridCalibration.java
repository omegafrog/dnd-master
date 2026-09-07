package com.dndmaster.combatmap.application.view;

import com.dndmaster.combatmap.domain.GridSpec;

public record GridCalibration(GridSpec grid, int originX, int originY, MapContentBounds bounds,
                               GridSource source, double confidence) {
    public GridCalibration {
        if (grid == null || bounds == null || source == null || originX < 0 || originY < 0
                || confidence < 0 || confidence > 1) throw new IllegalArgumentException("invalid grid calibration");
    }
    public String metadataValue() {
        return "source=" + source + ";confidence=" + confidence + ";crop=" + bounds.x() + "," + bounds.y()
                + "," + bounds.width() + "," + bounds.height() + ";origin=" + originX + "," + originY;
    }
}
