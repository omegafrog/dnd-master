package com.dndmaster.combatmap.application.view;

/** 사용자가 원본 맵 위에 맞춘 격자와 플레이어 시작 칸. */
public record GridCalibrationRequest(
        int width, int height, int cellSize, int originX, int originY,
        int imageWidth, int imageHeight, Integer playerX, Integer playerY) {
    public GridCalibrationRequest {
        if (width < 1 || height < 1 || cellSize < 1 || imageWidth < 1 || imageHeight < 1) {
            throw new IllegalArgumentException("grid calibration dimensions must be positive");
        }
        if (originX < 0 || originY < 0 || originX + width * cellSize > imageWidth || originY + height * cellSize > imageHeight) {
            throw new IllegalArgumentException("grid calibration must fit inside map image");
        }
        if ((playerX == null) != (playerY == null)
                || playerX != null && (playerX < 0 || playerX >= width || playerY < 0 || playerY >= height)) {
            throw new IllegalArgumentException("player start cell must be inside the grid");
        }
    }
}
