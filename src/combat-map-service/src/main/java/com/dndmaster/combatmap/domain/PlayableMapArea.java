package com.dndmaster.combatmap.domain;

import java.util.List;
import java.util.Optional;

/** Maps the reviewed image crop to the cells that are actually part of play. */
public final class PlayableMapArea {
    private PlayableMapArea() { }

    public static boolean contains(GridSpec grid, List<MapLayer> layers, GridPosition position) {
        if (!grid.contains(position)) return false;
        Optional<Rectangle> crop = layer(layers, "MAP_CROP").flatMap(PlayableMapArea::rectangle);
        Optional<GridBounds> bounds = layer(layers, "GRID_BOUNDS").flatMap(value -> GridBounds.parse(value, grid));
        if (crop.isEmpty() || bounds.isEmpty()) return true;
        GridBounds gridBounds = bounds.orElseThrow();
        Rectangle visibleCrop = crop.orElseThrow();
        double centerX = gridBounds.originX + (position.x() + .5d) * gridBounds.cellWidth;
        double centerY = gridBounds.originY + (position.y() + .5d) * gridBounds.cellHeight;
        return centerX >= visibleCrop.x && centerX < visibleCrop.right()
                && centerY >= visibleCrop.y && centerY < visibleCrop.bottom();
    }

    private static Optional<String> layer(List<MapLayer> layers, String type) {
        return layers.stream().filter(layer -> type.equals(layer.type())).map(MapLayer::value).findFirst();
    }

    private static Optional<Rectangle> rectangle(String value) {
        String[] values = value == null ? new String[0] : value.split(",", -1);
        if (values.length != 4) return Optional.empty();
        try {
            double x = Double.parseDouble(values[0].trim()), y = Double.parseDouble(values[1].trim());
            double width = Double.parseDouble(values[2].trim()), height = Double.parseDouble(values[3].trim());
            return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(width) && Double.isFinite(height)
                    && width > 0 && height > 0 ? Optional.of(new Rectangle(x, y, width, height)) : Optional.empty();
        } catch (NumberFormatException ignored) { return Optional.empty(); }
    }

    private record Rectangle(double x, double y, double width, double height) {
        double right() { return x + width; }
        double bottom() { return y + height; }
    }

    private record GridBounds(double originX, double originY, double cellWidth, double cellHeight) {
        static Optional<GridBounds> parse(String value, GridSpec grid) {
            String[] values = value == null ? new String[0] : value.split(",", -1);
            if (values.length < 4) return Optional.empty();
            try {
                double x = Double.parseDouble(values[0].trim()), y = Double.parseDouble(values[1].trim());
                double width = Double.parseDouble(values[2].trim()), height = Double.parseDouble(values[3].trim());
                return Double.isFinite(x) && Double.isFinite(y) && width > 0 && height > 0
                        ? Optional.of(new GridBounds(x, y, width / grid.width(), height / grid.height())) : Optional.empty();
            } catch (NumberFormatException ignored) { return Optional.empty(); }
        }
    }
}
