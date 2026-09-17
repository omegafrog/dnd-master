package com.dndmaster.combatmap.domain;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Combat Map이 소유하는 공간 요소 정본 엔티티. 점유 칸은 생성 뒤 이동하지 않는다. */
public final class SpatialFeature {
    public enum State {
        HIDDEN, DISCOVERED, REVEALED, DISARMED, TRIGGERED, REPEATABLE,
        OPEN, ACTIVE, AVAILABLE, REFLECTED, PERSISTING, RESOLVED, ENDED
    }

    private final UUID id;
    private final SpatialFeatureType type;
    private final Set<GridPosition> cells;
    private final DetectionSpec detectionSpec;
    private final Set<SpatialTrigger> triggers;
    private final SpatialFeatureProvenance provenance;
    private final boolean repeatable;
    private final String removalPolicy;
    private final boolean overlapAllowed;
    private int remainingDurationTurns;
    private SpatialFeatureVisibility visibility;
    private State state;

    public SpatialFeature(UUID id, SpatialFeatureType type, Collection<GridPosition> cells,
            SpatialFeatureVisibility visibility, State state, DetectionSpec detectionSpec,
            Collection<SpatialTrigger> triggers, SpatialFeatureProvenance provenance,
            boolean repeatable, int remainingDurationTurns) {
        this(id, type, cells, visibility, state, detectionSpec, triggers, provenance,
                repeatable, remainingDurationTurns, "", type == SpatialFeatureType.MAGICAL_AREA_EFFECT);
    }

    public SpatialFeature(UUID id, SpatialFeatureType type, Collection<GridPosition> cells,
            SpatialFeatureVisibility visibility, State state, DetectionSpec detectionSpec,
            Collection<SpatialTrigger> triggers, SpatialFeatureProvenance provenance,
            boolean repeatable, int remainingDurationTurns, String removalPolicy, boolean overlapAllowed) {
        this.id = Objects.requireNonNull(id, "feature id must not be null");
        this.type = Objects.requireNonNull(type, "feature type must not be null");
        this.cells = immutableCells(cells);
        this.visibility = Objects.requireNonNull(visibility, "feature visibility must not be null");
        this.state = Objects.requireNonNull(state, "feature state must not be null");
        this.detectionSpec = detectionSpec;
        this.triggers = Set.copyOf(Objects.requireNonNull(triggers, "feature triggers must not be null"));
        this.provenance = Objects.requireNonNull(provenance, "feature provenance must not be null");
        this.repeatable = repeatable;
        this.removalPolicy = removalPolicy == null ? "" : removalPolicy.trim();
        this.overlapAllowed = overlapAllowed;
        if (remainingDurationTurns < -1) throw new IllegalArgumentException("feature duration must be -1 or non-negative");
        this.remainingDurationTurns = remainingDurationTurns;
        if (type == SpatialFeatureType.MAGICAL_AREA_EFFECT && cells.isEmpty()) {
            throw new IllegalArgumentException("magical area effect requires fixed occupied cells");
        }
        if (visibility == SpatialFeatureVisibility.HIDDEN && state != State.HIDDEN) {
            throw new IllegalArgumentException("hidden feature must start in HIDDEN state");
        }
    }

    public static SpatialFeature hidden(UUID id, SpatialFeatureType type, Collection<GridPosition> cells,
            DetectionSpec detectionSpec, Collection<SpatialTrigger> triggers, SpatialFeatureProvenance provenance) {
        return new SpatialFeature(id, type, cells, SpatialFeatureVisibility.HIDDEN, State.HIDDEN,
                detectionSpec, triggers, provenance, false, -1);
    }

    public static SpatialFeature active(UUID id, SpatialFeatureType type, Collection<GridPosition> cells,
            SpatialFeatureProvenance provenance, int durationTurns) {
        return new SpatialFeature(id, type, cells, SpatialFeatureVisibility.REVEALED, State.ACTIVE,
                null, Set.of(), provenance, false, durationTurns);
    }

    public static SpatialFeature prepared(UUID id, SpatialFeatureType type, Collection<GridPosition> cells,
            DetectionSpec detectionSpec, Collection<SpatialTrigger> triggers,
            SpatialFeatureProvenance provenance, int durationTurns, String removalPolicy,
            boolean overlapAllowed) {
        boolean magical = type == SpatialFeatureType.MAGICAL_AREA_EFFECT;
        SpatialFeatureVisibility visibility = magical ? SpatialFeatureVisibility.REVEALED : SpatialFeatureVisibility.HIDDEN;
        State state = magical ? State.ACTIVE : State.HIDDEN;
        return new SpatialFeature(id, type, cells, visibility, state, detectionSpec, triggers, provenance,
                false, magical ? durationTurns : -1, removalPolicy, overlapAllowed);
    }

    public static SpatialFeature legacy(UUID id, SpatialFeatureType type, GridPosition cell,
            SpatialFeatureVisibility visibility) {
        if (type != SpatialFeatureType.TRAP && type != SpatialFeatureType.INTERACTIVE_OBJECT) {
            throw new IllegalArgumentException("legacy feature type must be TRAP or INTERACTIVE_OBJECT");
        }
        State state = visibility == SpatialFeatureVisibility.HIDDEN ? State.HIDDEN : State.REVEALED;
        return new SpatialFeature(id, type, Set.of(cell), visibility, state, null, Set.of(),
                SpatialFeatureProvenance.system("legacy-token", 0, 0), false, -1);
    }

    public void discover() {
        visibility = SpatialFeatureVisibility.DISCOVERED;
        if (state == State.HIDDEN) state = State.DISCOVERED;
    }

    public void reveal() {
        visibility = SpatialFeatureVisibility.REVEALED;
        if (state == State.HIDDEN || state == State.DISCOVERED) state = State.REVEALED;
    }

    public void disarm() {
        requireType(SpatialFeatureType.TRAP);
        state = State.DISARMED;
    }

    public void trigger() {
        if (type == SpatialFeatureType.SECRET_DOOR) throw new IllegalStateException("secret door needs interaction before opening");
        state = repeatable ? State.REPEATABLE : State.TRIGGERED;
    }

    public void open() {
        requireType(SpatialFeatureType.SECRET_DOOR);
        if (visibility == SpatialFeatureVisibility.HIDDEN) throw new IllegalStateException("secret door must be discovered before opening");
        state = State.OPEN;
    }

    public void resolve() {
        if (type == SpatialFeatureType.MAGICAL_AREA_EFFECT) throw new IllegalStateException("magical area effect must expire or be removed");
        state = State.RESOLVED;
    }

    public void advanceDuration() {
        if (remainingDurationTurns < 0) return;
        if (remainingDurationTurns == 0) { state = State.ENDED; return; }
        remainingDurationTurns--;
        if (remainingDurationTurns == 0) state = State.ENDED;
        else state = State.PERSISTING;
    }

    public UUID id() { return id; }
    public SpatialFeatureType type() { return type; }
    public Set<GridPosition> cells() { return cells; }
    public SpatialFeatureVisibility visibility() { return visibility; }
    public State state() { return state; }
    public DetectionSpec detectionSpec() { return detectionSpec; }
    public Set<SpatialTrigger> triggers() { return triggers; }
    public SpatialFeatureProvenance provenance() { return provenance; }
    public boolean repeatable() { return repeatable; }
    public String removalPolicy() { return removalPolicy; }
    public boolean overlapAllowed() { return overlapAllowed; }
    public int remainingDurationTurns() { return remainingDurationTurns; }

    private void requireType(SpatialFeatureType expected) {
        if (type != expected) throw new IllegalStateException("operation is not valid for " + type);
    }

    private static Set<GridPosition> immutableCells(Collection<GridPosition> cells) {
        Objects.requireNonNull(cells, "feature cells must not be null");
        if (cells.isEmpty()) throw new IllegalArgumentException("feature must occupy at least one cell");
        LinkedHashSet<GridPosition> result = new LinkedHashSet<>();
        for (GridPosition cell : cells) result.add(Objects.requireNonNull(cell, "feature cell must not be null"));
        return Set.copyOf(result);
    }
}
