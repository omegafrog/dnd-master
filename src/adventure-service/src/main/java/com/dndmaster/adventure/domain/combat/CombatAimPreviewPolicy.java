package com.dndmaster.adventure.domain.combat;

import com.dndmaster.adventure.domain.scenario.StructuredSpellDefinition;
import java.util.ArrayList;
import java.util.List;

/** Pure preview geometry driven by a reviewed action definition and public map cells. */
public final class CombatAimPreviewPolicy {
    private CombatAimPreviewPolicy() { }

    public static Preview preview(StructuredSpellDefinition definition, Cell origin, Cell target,
            int feetPerCell, int gridWidth, int gridHeight, List<Cell> publicBlockers) {
        return preview(definition, origin, 0, target, 0, feetPerCell, gridWidth, gridHeight, publicBlockers, List.of());
    }

    public static Preview preview(StructuredSpellDefinition definition, Cell origin, int originElevationFeet,
            Cell target, int targetElevationFeet, int feetPerCell, int gridWidth, int gridHeight,
            List<Cell> publicBlockers) {
        return preview(definition, origin, originElevationFeet, target, targetElevationFeet, feetPerCell,
                gridWidth, gridHeight, publicBlockers, List.of(), List.of());
    }

    public static Preview preview(StructuredSpellDefinition definition, Cell origin, int originElevationFeet,
            Cell target, int targetElevationFeet, int feetPerCell, int gridWidth, int gridHeight,
            List<Cell> publicBlockers, List<Boundary> publicBoundaries) {
        return preview(definition, origin, originElevationFeet, target, targetElevationFeet, feetPerCell,
                gridWidth, gridHeight, publicBlockers, publicBoundaries, List.of());
    }

    public static Preview preview(StructuredSpellDefinition definition, Cell origin, int originElevationFeet,
            Cell target, int targetElevationFeet, int feetPerCell, int gridWidth, int gridHeight,
            List<Cell> publicBlockers, List<Boundary> publicBoundaries, List<ElevatedCell> publicTokens) {
        if (definition == null || !definition.active() || definition.reviewStatus() != StructuredSpellDefinition.ReviewStatus.APPROVED
                || definition.rangeProfile() == null || !definition.rangeProfile().supported()
                || definition.targetingProfile() == null || !definition.targetingProfile().supported()) {
            return Preview.unsupported();
        }
        if (origin == null || target == null || originElevationFeet < 0 || targetElevationFeet < 0
                || feetPerCell <= 0 || gridWidth <= 0 || gridHeight <= 0
                || !inside(origin, gridWidth, gridHeight) || !inside(target, gridWidth, gridHeight)) {
            return Preview.invalid();
        }
        var targeting = definition.targetingProfile();
        int distance = gridCellsBetween(origin, originElevationFeet, target, targetElevationFeet, feetPerCell) * feetPerCell;
        int normal = definition.rangeProfile().normalRangeFeet();
        int maximum = definition.rangeProfile().form() == StructuredSpellDefinition.RangeForm.NORMAL_AND_LONG
                ? definition.rangeProfile().longRangeFeet() : normal;
        boolean inRange = distance <= maximum;
        boolean longRange = inRange && distance > normal;
        List<Cell> path = line(origin, target);
        int blockerIndex = targeting.pathBlocking() == StructuredSpellDefinition.PathBlockingRule.NONE ? -1
                : firstBlockedStep(path, publicBlockers, publicBoundaries);
        boolean clearPath = blockerIndex < 0;
        if (blockerIndex > 0) path = path.subList(0, blockerIndex);
        List<Cell> effect = effectCells(targeting, origin, target, feetPerCell, gridWidth, gridHeight, path,
                publicBlockers, publicBoundaries, targetElevationFeet, publicTokens);
        if ((targeting.propagation() == StructuredSpellDefinition.PropagationRule.CONTIGUOUS
                || targeting.propagation() == StructuredSpellDefinition.PropagationRule.DIRECT) && publicBlockers != null) {
            Cell propagationOrigin = targeting.origin() == StructuredSpellDefinition.OriginMode.ACTOR_TOKEN ? origin : target;
            effect = effect.stream().filter(cell -> firstBlockedStep(line(propagationOrigin, cell), publicBlockers,
                    publicBoundaries) < 0).toList();
        }
        return new Preview(true, origin, target, distance, inRange, longRange, path, clearPath,
                !inRange ? "OUT_OF_RANGE" : clearPath ? null : "PUBLIC_BLOCKER", effect);
    }

    private static List<Cell> effectCells(StructuredSpellDefinition.TargetingProfile profile, Cell origin, Cell target,
            int feetPerCell, int width, int height, List<Cell> path, List<Cell> blockers, List<Boundary> boundaries,
            int targetElevationFeet, List<ElevatedCell> publicTokens) {
        if (profile.shape() == StructuredSpellDefinition.EffectShape.NONE
                || profile.propagation() == StructuredSpellDefinition.PropagationRule.NONE) return List.of();
        int length = value(profile.lengthFeet(), 0) / feetPerCell;
        int halfWidth = Math.max(0, value(profile.widthFeet(),
                profile.shape() == StructuredSpellDefinition.EffectShape.CUBE ? profile.lengthFeet() : feetPerCell) / feetPerCell / 2);
        Cell shapeOrigin = profile.origin() == StructuredSpellDefinition.OriginMode.ACTOR_TOKEN ? origin : target;
        List<Cell> cells = new ArrayList<>();
        double dx = target.x() - origin.x(), dy = target.y() - origin.y();
        double magnitude = Math.max(1, Math.hypot(dx, dy));
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            Cell cell = new Cell(x, y);
            double px = x - shapeOrigin.x(), py = y - shapeOrigin.y();
            boolean included = switch (profile.shape()) {
                case SPHERE, CYLINDER -> containsEffectTarget(profile, target, targetElevationFeet, cell, 0, feetPerCell)
                        || publicTokens != null && publicTokens.stream().filter(token -> token.cell().equals(cell))
                                .anyMatch(token -> containsEffectTarget(profile, target, targetElevationFeet,
                                        cell, token.elevationFeet(), feetPerCell));
                case CUBE -> profile.origin() == StructuredSpellDefinition.OriginMode.CURSOR_POINT
                        ? Math.abs(px) <= halfWidth && Math.abs(py) <= halfWidth
                        : projection(px, py, dx, dy, magnitude) >= 0
                        && projection(px, py, dx, dy, magnitude) <= length
                        && Math.abs(perpendicular(px, py, dx, dy, magnitude)) <= halfWidth;
                case WALL -> projection(px, py, dx, dy, magnitude) >= 0
                        && projection(px, py, dx, dy, magnitude) <= length
                        && Math.abs(perpendicular(px, py, dx, dy, magnitude)) <= halfWidth;
                case CONE -> projection(px, py, dx, dy, magnitude) >= 0
                        && projection(px, py, dx, dy, magnitude) <= length
                        && Math.abs(perpendicular(px, py, dx, dy, magnitude)) <= projection(px, py, dx, dy, magnitude);
                case LINE -> projection(px, py, dx, dy, magnitude) >= 0
                        && projection(px, py, dx, dy, magnitude) <= length
                        && Math.abs(perpendicular(px, py, dx, dy, magnitude)) <= halfWidth;
                default -> false;
            };
            if (included) cells.add(cell);
        }
        return List.copyOf(cells);
    }

    private static double projection(double x, double y, double dx, double dy, double magnitude) { return (x * dx + y * dy) / magnitude; }
    private static double perpendicular(double x, double y, double dx, double dy, double magnitude) { return Math.abs(x * dy - y * dx) / magnitude; }
    private static int value(Integer value, int fallback) { return value == null ? fallback : value; }
    private static boolean inside(Cell cell, int width, int height) { return cell.x() >= 0 && cell.y() >= 0 && cell.x() < width && cell.y() < height; }
    private static boolean contains(List<Cell> cells, Cell cell) { return cells != null && cells.contains(cell); }
    private static int firstBlockedStep(List<Cell> path, List<Cell> blockers, List<Boundary> boundaries) {
        for (int index = 1; index < path.size(); index++) {
            Cell from = path.get(index - 1), to = path.get(index);
            if (contains(blockers, to) || (boundaries != null && boundaries.stream().anyMatch(edge -> edge.blocks(from, to)))) return index;
        }
        return -1;
    }
    private static int cellsBetween(Cell left, Cell right) { return Math.max(Math.abs(left.x() - right.x()), Math.abs(left.y() - right.y())); }
    private static int gridCellsBetween(Cell from, int fromElevationFeet, Cell to, int toElevationFeet, int feetPerCell) {
        int horizontal = cellsBetween(from, to);
        int vertical = (int) ((Math.abs((long) toElevationFeet - fromElevationFeet) + feetPerCell - 1L) / feetPerCell);
        return Math.max(horizontal, vertical);
    }

    /** Tests a candidate map cell or public token against the saved 3D area dimensions. */
    public static boolean containsEffectTarget(StructuredSpellDefinition.TargetingProfile profile,
            Cell center, int centerElevationFeet, Cell candidate, int candidateElevationFeet, int feetPerCell) {
        if (profile == null || center == null || candidate == null || feetPerCell <= 0
                || centerElevationFeet < 0 || candidateElevationFeet < 0) return false;
        int radiusCells = value(profile.radiusFeet(), 0) / feetPerCell;
        return switch (profile.shape()) {
            case SPHERE -> gridCellsBetween(center, centerElevationFeet, candidate, candidateElevationFeet, feetPerCell)
                    <= radiusCells;
            case CYLINDER -> profile.verticalDirection() != null
                    && profile.verticalDirection() != StructuredSpellDefinition.VerticalDirection.UNSUPPORTED
                    && (profile.verticalDirection() == StructuredSpellDefinition.VerticalDirection.UP
                            ? candidateElevationFeet >= centerElevationFeet : candidateElevationFeet <= centerElevationFeet)
                    && cellsBetween(center, candidate) <= radiusCells
                    && gridCellsBetween(center, centerElevationFeet, candidate, candidateElevationFeet, feetPerCell)
                            * feetPerCell <= value(profile.heightFeet(), 0);
            default -> false;
        };
    }

    private static List<Cell> line(Cell from, Cell to) {
        List<Cell> cells = new ArrayList<>();
        int x = from.x(), y = from.y();
        int dx = Math.abs(to.x() - x), sx = x < to.x() ? 1 : -1;
        int dy = -Math.abs(to.y() - y), sy = y < to.y() ? 1 : -1, error = dx + dy;
        while (true) {
            cells.add(new Cell(x, y));
            if (x == to.x() && y == to.y()) break;
            int twice = 2 * error;
            if (twice >= dy) { error += dy; x += sx; }
            if (twice <= dx) { error += dx; y += sy; }
        }
        return List.copyOf(cells);
    }

    public record Cell(int x, int y) { }
    public record ElevatedCell(Cell cell, int elevationFeet) { }
    public record Boundary(int x, int y, String orientation, String kind, boolean open) {
        boolean blocks(Cell from, Cell to) {
            if ("DOOR".equals(kind) && open) return false;
            int dx = to.x() - from.x(), dy = to.y() - from.y();
            if (Math.abs(dx) > 1 || Math.abs(dy) > 1 || (dx == 0 && dy == 0)) return false;
            boolean horizontal = "HORIZONTAL".equals(orientation)
                    && ((dy > 0 && x == from.x() && y == from.y() + 1)
                    || (dy < 0 && x == from.x() && y == from.y()));
            boolean vertical = "VERTICAL".equals(orientation)
                    && ((dx > 0 && x == from.x() + 1 && y == from.y())
                    || (dx < 0 && x == from.x() && y == from.y()));
            return horizontal || vertical;
        }
    }
    public record Preview(boolean supported, Cell origin, Cell target, int distanceFeet, boolean inRange,
            boolean longRange, List<Cell> path, boolean clearPath, String blockReason, List<Cell> effectCells) {
        public Preview { path = List.copyOf(path); effectCells = List.copyOf(effectCells); }
        static Preview unsupported() { return new Preview(false, null, null, 0, false, false, List.of(), false, "UNSUPPORTED_DEFINITION", List.of()); }
        static Preview invalid() { return new Preview(true, null, null, 0, false, false, List.of(), false, "INVALID_POSITION", List.of()); }
    }
}
