package com.dndmaster.combatmap.application.view;

import com.dndmaster.combatmap.domain.GridPosition;
import com.dndmaster.combatmap.domain.GridSpec;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Optional;
import javax.imageio.ImageIO;

/** 이미지에서 찾은 진입 지점을 사용자가 확정한 격자 칸으로 변환한다. */
public final class EntryPlacementProjector {
    public Optional<Geometry> geometry(MapGenerationRequest request) {
        if (!request.gridConfirmed() || request.mapImage() == null) return Optional.empty();
        try (ByteArrayInputStream input = new ByteArrayInputStream(request.mapImage().content())) {
            var image = ImageIO.read(input);
            if (image == null || image.getWidth() < 1 || image.getHeight() < 1) return Optional.empty();
            return Optional.of(new Geometry(new GridSpec(request.gridWidth(), request.gridHeight(),
                    request.cellSize(), request.distanceUnit()), request.gridOriginX(), request.gridOriginY(),
                    request.gridCellSize(), image.getWidth(), image.getHeight()));
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    public record Geometry(GridSpec grid, double originX, double originY, double cellSize,
                           int imageWidth, int imageHeight) {
        public Geometry {
            if (grid == null || !Double.isFinite(originX) || !Double.isFinite(originY)
                    || !Double.isFinite(cellSize) || cellSize <= 0 || imageWidth < 1 || imageHeight < 1) {
                throw new IllegalArgumentException("invalid entry placement image geometry");
            }
        }

        public Optional<GridPosition> project(double xNormalized, double yNormalized) {
            if (!Double.isFinite(xNormalized) || !Double.isFinite(yNormalized)
                    || xNormalized < 0 || xNormalized > 1 || yNormalized < 0 || yNormalized > 1) return Optional.empty();
            double imageX = Math.min(Math.nextDown((double) imageWidth), xNormalized * imageWidth);
            double imageY = Math.min(Math.nextDown((double) imageHeight), yNormalized * imageHeight);
            int projectedX = (int) Math.floor((imageX - originX) / cellSize);
            int projectedY = (int) Math.floor((imageY - originY) / cellSize);
            // The model may identify the edge of an entrance graphic just
            // outside the confirmed grid rectangle. Accept only that small
            // boundary error and snap it to the nearest edge cell; larger
            // errors remain unresolved instead of inventing a location.
            if (projectedX < -1 || projectedX > grid.width()
                    || projectedY < -1 || projectedY > grid.height()) return Optional.empty();
            GridPosition position = new GridPosition(
                    Math.max(0, Math.min(grid.width() - 1, projectedX)),
                    Math.max(0, Math.min(grid.height() - 1, projectedY)));
            return Optional.of(position);
        }
    }
}
