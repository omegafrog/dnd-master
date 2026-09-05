package com.dndmaster.adventure.domain.combat;

import java.util.Objects;

/** Deterministic grid movement rules shared by mapped and mapless commands. */
public final class CombatMovementPolicy {
    public static final int GRID_DISTANCE_UNIT = 5;

    private CombatMovementPolicy() {}

    public static int distanceOf(String path) {
        Objects.requireNonNull(path, "movement path must not be null");
        String[] positions = path.split("[>;]");
        if (positions.length < 2) throw new IllegalArgumentException("movement path requires a destination");
        if (positions.length - 1 > 256) throw new IllegalArgumentException("movement path exceeds 256 edges");
        for (String position : positions) parse(position);
        return Math.multiplyExact(positions.length - 1, GRID_DISTANCE_UNIT);
    }

    private static void parse(String value) {
        try {
            String trimmed = value.trim().toUpperCase();
            String[] coordinates = trimmed.split(",");
            int x;
            int y;
            if (coordinates.length == 2) {
                x = Integer.parseInt(coordinates[0].trim());
                y = Integer.parseInt(coordinates[1].trim());
            } else {
                int split = 0;
                while (split < trimmed.length() && Character.isLetter(trimmed.charAt(split))) split++;
                if (split == 0 || split == trimmed.length()) throw new NumberFormatException();
                x = 0;
                for (int index = 0; index < split; index++) x = x * 26 + trimmed.charAt(index) - 'A' + 1;
                x--;
                y = Integer.parseInt(trimmed.substring(split)) - 1;
            }
            if (x < 0 || y < 0) {
                throw new IllegalArgumentException("movement positions must be non-negative");
            }
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("movement path must use x,y positions", exception);
        }
    }
}
