package com.dndmaster.adventure.application.combat;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CombatMapViewPort {
    Optional<View> playerView(UUID adventureId, UUID ownerId);
    default Optional<View> preparationView(UUID adventureId, UUID ownerId) { return playerView(adventureId, ownerId); }

    default Alignment alignment(UUID mapId, UUID ownerId) { throw new UnsupportedOperationException("map grid alignment unavailable"); }

    default Alignment applyAlignment(UUID mapId, UUID ownerId, AlignmentRequest request) { throw new UnsupportedOperationException("map grid alignment unavailable"); }

    default byte[] alignmentImage(UUID mapId, UUID ownerId, String imageViewId) { throw new UnsupportedOperationException("public map image unavailable"); }
    default byte[] preparationImage(UUID mapId, UUID ownerId) { throw new UnsupportedOperationException("map preparation image unavailable"); }
    default BoundaryProposal detectMapBoundaries(UUID mapId, UUID ownerId) { throw new UnsupportedOperationException("map boundary detection unavailable"); }

    default void calibrate(UUID mapId, UUID ownerId, long expectedVersion, int width, int height, int cellSize,
            int originX, int originY, int imageWidth, int imageHeight, Integer playerX, Integer playerY) {
        throw new UnsupportedOperationException("combat map calibration unavailable");
    }
    default void updateLayout(UUID mapId, UUID ownerId, long expectedVersion, UUID commandId, List<Position> obstacles, List<Door> doors, String crop) {
        throw new UnsupportedOperationException("combat map layout editing unavailable");
    }
    default void updateLayout(UUID mapId, UUID ownerId, long expectedVersion, UUID commandId, List<Position> obstacles, List<Door> doors, List<Boundary> boundaries, String crop) {
        updateLayout(mapId, ownerId, expectedVersion, commandId, obstacles, doors, crop);
    }
    default void updateLayout(UUID mapId, UUID ownerId, long expectedVersion, UUID commandId, List<Position> obstacles,
            List<Door> doors, List<Boundary> boundaries, String crop, Long alignmentVersion, String imageRevision) {
        updateLayout(mapId, ownerId, expectedVersion, commandId, obstacles, doors, boundaries, crop);
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
    record Boundary(int x, int y, String orientation, String kind, boolean open) {}
    record BoundaryCandidate(int x, int y, String orientation, String kind, double confidence, List<String> evidence, String source) {
        public BoundaryCandidate {
            evidence = evidence == null ? List.of() : List.copyOf(evidence);
            source = source == null || source.isBlank() ? "IMAGE_RULES" : source;
        }
    }
    record BoundaryProposal(long mapVersion, List<Position> obstacles, List<Door> doors, List<Boundary> boundaries, String crop,
                            List<BoundaryCandidate> candidates, long alignmentVersion, String imageRevision) {
        public BoundaryProposal(long mapVersion, List<Position> obstacles, List<Door> doors, List<Boundary> boundaries, String crop) {
            this(mapVersion, obstacles, doors, boundaries, crop, List.of(), 0, "");
        }
        public BoundaryProposal(long mapVersion, List<Position> obstacles, List<Door> doors, List<Boundary> boundaries,
                String crop, List<BoundaryCandidate> candidates) {
            this(mapVersion, obstacles, doors, boundaries, crop, candidates, 0, "");
        }
        public BoundaryProposal {
            obstacles = obstacles == null ? List.of() : List.copyOf(obstacles);
            doors = doors == null ? List.of() : List.copyOf(doors);
            boundaries = boundaries == null ? List.of() : List.copyOf(boundaries);
            crop = crop == null ? "" : crop;
            candidates = candidates == null ? List.of() : List.copyOf(candidates);
            imageRevision = imageRevision == null ? "" : imageRevision;
        }
    }
    record Layer(String type, String value) {}
    record Position(int x, int y) {}
    record Alignment(UUID mapId, long version, String imageRevision, String imageViewId, double originX, double originY, double cellSize) {}
    record AlignmentRequest(UUID commandId, long expectedVersion, String imageRevision, double originX, double originY, double cellSize) {}
}
