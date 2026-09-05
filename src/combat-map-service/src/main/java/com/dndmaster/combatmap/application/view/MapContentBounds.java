package com.dndmaster.combatmap.application.view;

public record MapContentBounds(int x, int y, int width, int height, double confidence) {
    public MapContentBounds {
        if (x < 0 || y < 0 || width < 1 || height < 1 || confidence < 0 || confidence > 1)
            throw new IllegalArgumentException("invalid map content bounds");
    }
    public boolean isFull(int imageWidth, int imageHeight) {
        return x == 0 && y == 0 && width == imageWidth && height == imageHeight;
    }
}
