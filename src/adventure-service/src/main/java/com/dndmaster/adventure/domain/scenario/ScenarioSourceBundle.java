package com.dndmaster.adventure.domain.scenario;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ScenarioSourceBundle {
    private final ScenarioBundleId id;
    private final OwnerPlayerId ownerPlayerId;
    private final String name;
    private final RulebookEdition rulebookEdition;
    private final List<ScenarioSourceBundleRevision> revisions;

    private ScenarioSourceBundle(
            ScenarioBundleId id, OwnerPlayerId ownerPlayerId, String name, RulebookEdition rulebookEdition,
            List<ScenarioSourceBundleRevision> revisions) {
        this.id = Objects.requireNonNull(id, "bundle id must not be null");
        this.ownerPlayerId = Objects.requireNonNull(ownerPlayerId, "owner player id must not be null");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("bundle name must not be blank");
        this.name = name.trim();
        this.rulebookEdition = Objects.requireNonNull(rulebookEdition, "rulebook edition must not be null");
        this.revisions = List.copyOf(Objects.requireNonNull(revisions, "revisions must not be null"));
        if (this.revisions.isEmpty()) {
            throw new IllegalArgumentException("bundle must have at least one revision");
        }
    }

    public static ScenarioSourceBundle create(
            ScenarioBundleId id, OwnerPlayerId ownerPlayerId, ScenarioSourceBundleRevision revision) {
        return create(id, ownerPlayerId, "Unnamed adventure", RulebookEdition.DND_5E_2014, revision);
    }

    public static ScenarioSourceBundle create(
            ScenarioBundleId id, OwnerPlayerId ownerPlayerId, String name, RulebookEdition rulebookEdition,
            ScenarioSourceBundleRevision revision) {
        return new ScenarioSourceBundle(id, ownerPlayerId, name, rulebookEdition, List.of(revision));
    }

    public static ScenarioSourceBundle rehydrate(
            ScenarioBundleId id, OwnerPlayerId ownerPlayerId, List<ScenarioSourceBundleRevision> revisions) {
        return new ScenarioSourceBundle(id, ownerPlayerId, "Unnamed adventure", RulebookEdition.DND_5E_2014, revisions);
    }

    public static ScenarioSourceBundle rehydrate(
            ScenarioBundleId id, OwnerPlayerId ownerPlayerId, String name, RulebookEdition rulebookEdition,
            List<ScenarioSourceBundleRevision> revisions) {
        return new ScenarioSourceBundle(id, ownerPlayerId, name, rulebookEdition, revisions);
    }

    public ScenarioSourceBundle revise(ScenarioSourceBundleRevision revision) {
        return revise(name, rulebookEdition, revision);
    }

    public ScenarioSourceBundle revise(String name, RulebookEdition rulebookEdition, ScenarioSourceBundleRevision revision) {
        List<ScenarioSourceBundleRevision> next = new ArrayList<>(revisions);
        next.add(revision);
        return new ScenarioSourceBundle(id, ownerPlayerId, name, rulebookEdition, next);
    }

    public void authorize(OwnerPlayerId requester) {
        if (!ownerPlayerId.equals(Objects.requireNonNull(requester, "requester must not be null"))) {
            throw new ScenarioBundleAccessDeniedException();
        }
    }

    public ScenarioSourceBundleRevision currentRevision() {
        return revisions.get(revisions.size() - 1);
    }

    public ScenarioBundleId id() {
        return id;
    }

    public OwnerPlayerId ownerPlayerId() {
        return ownerPlayerId;
    }

    public String name() { return name; }

    public RulebookEdition rulebookEdition() { return rulebookEdition; }

    public List<ScenarioSourceBundleRevision> revisions() {
        return revisions;
    }
}
