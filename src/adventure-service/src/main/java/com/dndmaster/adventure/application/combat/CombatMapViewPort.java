package com.dndmaster.adventure.application.combat;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CombatMapViewPort {
    Optional<View> playerView(UUID adventureId, UUID ownerId);

    record View(UUID mapId, Grid grid, List<Token> tokens, List<Obstacle> obstacles, List<Layer> layers,
            List<Position> current, List<Position> explored, long version) {}
    record Grid(int width, int height, int cellSize, int distanceUnit) {}
    record Token(UUID id, String type, int x, int y) {}
    record Obstacle(int x, int y) {}
    record Layer(String type, String value) {}
    record Position(int x, int y) {}
}
