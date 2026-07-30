package com.dndmaster.adventure.domain.adventure;

import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class RuntimeBinding {
    private final AdventureId adventureId;
    private final OwnerPlayerId ownerPlayerId;
    private final long bindingVersion;
    private final UUID scenarioPackageId;
    private final long scenarioPackageRevision;
    private final List<UUID> rulebookIds;
    private final List<AdventurePartyMember> party;
    private final String engineId;
    private final List<String> toolIds;
    private final PlayabilityReport playabilityReport;
    private final ActiveSourceContext activeSourceContext;

    private RuntimeBinding(
            AdventureId adventureId,
            OwnerPlayerId ownerPlayerId,
            long bindingVersion,
            UUID scenarioPackageId,
            long scenarioPackageRevision,
            List<UUID> rulebookIds,
            List<AdventurePartyMember> party,
            String engineId,
            List<String> toolIds,
            PlayabilityReport playabilityReport,
            ActiveSourceContext activeSourceContext) {
        this.adventureId = Objects.requireNonNull(adventureId, "adventure id must not be null");
        this.ownerPlayerId = Objects.requireNonNull(ownerPlayerId, "owner player id must not be null");
        if (bindingVersion <= 0) {
            throw new IllegalArgumentException("binding version must be positive");
        }
        this.bindingVersion = bindingVersion;
        this.scenarioPackageId = Objects.requireNonNull(scenarioPackageId, "scenario package id must not be null");
        if (scenarioPackageRevision <= 0) {
            throw new IllegalArgumentException("scenario package revision must be positive");
        }
        this.scenarioPackageRevision = scenarioPackageRevision;
        this.rulebookIds = List.copyOf(Objects.requireNonNull(rulebookIds, "rulebook ids must not be null"));
        this.party = List.copyOf(Objects.requireNonNull(party, "party must not be null"));
        if (this.party.isEmpty()) throw new IllegalArgumentException("party must not be empty");
        this.engineId = required(engineId, "engine id");
        this.toolIds = List.copyOf(Objects.requireNonNull(toolIds, "tool ids must not be null"));
        this.playabilityReport = Objects.requireNonNull(playabilityReport, "playability report must not be null");
        this.activeSourceContext = activeSourceContext;
    }

    public static RuntimeBinding create(
            AdventureId adventureId,
            OwnerPlayerId ownerPlayerId,
            UUID scenarioPackageId,
            long scenarioPackageRevision,
            List<UUID> rulebookIds,
            List<AdventurePartyMember> party,
            String engineId,
            List<String> toolIds,
            PlayabilityReport playabilityReport,
            ActiveSourceContext activeSourceContext) {
        return new RuntimeBinding(
                adventureId, ownerPlayerId, 1, scenarioPackageId, scenarioPackageRevision, rulebookIds,
                party, engineId, toolIds, playabilityReport, activeSourceContext);
    }
    public static RuntimeBinding rehydrate(
            AdventureId adventureId,
            OwnerPlayerId ownerPlayerId,
            long bindingVersion,
            UUID scenarioPackageId,
            long scenarioPackageRevision,
            List<UUID> rulebookIds,
            List<AdventurePartyMember> party,
            String engineId,
            List<String> toolIds,
            PlayabilityReport playabilityReport,
            ActiveSourceContext activeSourceContext) {
        return new RuntimeBinding(
                adventureId, ownerPlayerId, bindingVersion, scenarioPackageId, scenarioPackageRevision, rulebookIds,
                party, engineId, toolIds, playabilityReport, activeSourceContext);
    }
    public RuntimeBinding withNewPackage(
            UUID scenarioPackageId,
            long scenarioPackageRevision,
            PlayabilityReport playabilityReport,
            ActiveSourceContext activeSourceContext) {
        return new RuntimeBinding(
                adventureId, ownerPlayerId, bindingVersion + 1, scenarioPackageId, scenarioPackageRevision,
                rulebookIds, party, engineId, toolIds, playabilityReport, activeSourceContext);
    }

    public RuntimeBinding withActiveSourceContext(PlayabilityReport playabilityReport, ActiveSourceContext activeSourceContext) {
        return new RuntimeBinding(
                adventureId, ownerPlayerId, bindingVersion, scenarioPackageId, scenarioPackageRevision,
                rulebookIds, party, engineId, toolIds, playabilityReport, activeSourceContext);
    }

    public AdventureId adventureId() { return adventureId; }
    public OwnerPlayerId ownerPlayerId() { return ownerPlayerId; }
    public long bindingVersion() { return bindingVersion; }
    public UUID scenarioPackageId() { return scenarioPackageId; }
    public long scenarioPackageRevision() { return scenarioPackageRevision; }
    /**
     * Legacy compatibility field. Runtime retrieval uses SessionKnowledgeSet instead.
     */
    @Deprecated(forRemoval = false)
    public List<UUID> rulebookIds() { return rulebookIds; }
    public List<AdventurePartyMember> party() { return party; }
    public String engineId() { return engineId; }
    public List<String> toolIds() { return toolIds; }
    public PlayabilityReport playabilityReport() { return playabilityReport; }
    public ActiveSourceContext activeSourceContext() { return activeSourceContext; }

    public RuntimeBinding withSelection(ActiveSourceContext selected, PlayabilityReport playabilityReport) {
        return new RuntimeBinding(
                adventureId, ownerPlayerId, bindingVersion, scenarioPackageId, scenarioPackageRevision,
                rulebookIds, party, engineId, toolIds, playabilityReport, selected);
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
