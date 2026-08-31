package com.dndmaster.combatmap.application.view;

/** Pixel geometry detected from the map's printed grid. */
public record DetectedMapGrid(int width, int height, int cellSize, int originX, int originY,
                              double confidence) {
    public DetectedMapGrid {
        if (width < 1 || height < 1 || cellSize < 1 || originX < 0 || originY < 0
                || confidence < 0 || confidence > 1) throw new IllegalArgumentException("invalid detected grid");
    }
    public String boundsValue(int imageWidth, int imageHeight) {
        return originX + "," + originY + "," + (width * cellSize) + "," + (height * cellSize)
                + "," + imageWidth + "," + imageHeight;
    }
}
