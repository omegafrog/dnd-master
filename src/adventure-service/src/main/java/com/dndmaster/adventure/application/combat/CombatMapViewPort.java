package com.dndmaster.adventure.application.combat;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CombatMapViewPort {
    Optional<View> playerView(UUID adventureId, UUID ownerId);

    default Alignment alignment(UUID mapId, UUID ownerId) { throw new UnsupportedOperationException("map grid alignment unavailable"); }

    default Alignment applyAlignment(UUID mapId, UUID ownerId, AlignmentRequest request) { throw new UnsupportedOperationException("map grid alignment unavailable"); }

    default void calibrate(UUID mapId, UUID ownerId, long expectedVersion, int width, int height, int cellSize,
            int originX, int originY, int imageWidth, int imageHeight, Integer playerX, Integer playerY) {
        throw new UnsupportedOperationException("combat map calibration unavailable");
    }

    record View(UUID mapId, Grid grid, List<Token> tokens, List<Obstacle> obstacles, List<Door> doors, List<Layer> layers,
            List<Position> current, List<Position> explored, long version) {
        public View(UUID mapId, Grid grid, List<Token> tokens, List<Obstacle> obstacles, List<Layer> layers,
                List<Position> current, List<Position> explored, long version) {
            this(mapId, grid, tokens, obstacles, List.of(), layers, current, explored, version);
        }
    }
    record Grid(int width, int height, int cellSize, int distanceUnit) {}
    record Token(UUID id, String type, int x, int y) {}
    record Obstacle(int x, int y) {}
    record Door(int x, int y, boolean open) {}
    record Layer(String type, String value) {}
    record Position(int x, int y) {}
    record Alignment(UUID mapId, long version, String imageRevision, double originX, double originY, double cellSize) {}
    record AlignmentRequest(UUID commandId, long expectedVersion, String imageRevision, double originX, double originY, double cellSize) {}
}
