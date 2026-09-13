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
            GridPosition position = new GridPosition((int) Math.floor((imageX - originX) / cellSize),
                    (int) Math.floor((imageY - originY) / cellSize));
            return grid.contains(position) ? Optional.of(position) : Optional.empty();
        }
    }
}
