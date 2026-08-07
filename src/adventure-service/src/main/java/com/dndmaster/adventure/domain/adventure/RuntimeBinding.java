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
    private final long gameSystemDefinitionVersion;
    private final long characterBlueprintVersion;
    private final PlayabilityReport playabilityReport;
    private final ActiveSourceContext activeSourceContext;
    private final RuntimeReadiness readiness;

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
            long gameSystemDefinitionVersion,
            long characterBlueprintVersion,
            PlayabilityReport playabilityReport,
            ActiveSourceContext activeSourceContext) {
        this(adventureId, ownerPlayerId, bindingVersion, scenarioPackageId, scenarioPackageRevision, rulebookIds,
                party, engineId, toolIds, gameSystemDefinitionVersion, characterBlueprintVersion, playabilityReport,
                activeSourceContext, readiness(bindingVersion, playabilityReport));
    }

    private RuntimeBinding(
            AdventureId adventureId, OwnerPlayerId ownerPlayerId, long bindingVersion, UUID scenarioPackageId,
            long scenarioPackageRevision, List<UUID> rulebookIds, List<AdventurePartyMember> party, String engineId,
            List<String> toolIds, long gameSystemDefinitionVersion, long characterBlueprintVersion,
            PlayabilityReport playabilityReport, ActiveSourceContext activeSourceContext, RuntimeReadiness readiness) {
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
        if (gameSystemDefinitionVersion < 0 || characterBlueprintVersion < 0) throw new IllegalArgumentException("binding references must not be negative");
        this.gameSystemDefinitionVersion = gameSystemDefinitionVersion;
        this.characterBlueprintVersion = characterBlueprintVersion;
        this.playabilityReport = Objects.requireNonNull(playabilityReport, "playability report must not be null");
        this.activeSourceContext = activeSourceContext;
        this.readiness = Objects.requireNonNull(readiness, "readiness must not be null");
        if (readiness.bindingVersion() != bindingVersion) throw new IllegalArgumentException("readiness binding version mismatch");
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
                party, engineId, toolIds, 0, 0, playabilityReport, activeSourceContext);
    }
    public static RuntimeBinding create(AdventureId adventureId, OwnerPlayerId ownerPlayerId, UUID scenarioPackageId,
            long scenarioPackageRevision, List<UUID> rulebookIds, List<AdventurePartyMember> party, String engineId,
            List<String> toolIds, long definitionVersion, long blueprintVersion, PlayabilityReport playabilityReport,
            ActiveSourceContext activeSourceContext) {
        return new RuntimeBinding(adventureId, ownerPlayerId, 1, scenarioPackageId, scenarioPackageRevision, rulebookIds,
                party, engineId, toolIds, definitionVersion, blueprintVersion, playabilityReport, activeSourceContext);
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
                party, engineId, toolIds, 0, 0, playabilityReport, activeSourceContext);
    }
    public static RuntimeBinding rehydrate(AdventureId adventureId, OwnerPlayerId ownerPlayerId, long bindingVersion,
            UUID scenarioPackageId, long scenarioPackageRevision, List<UUID> rulebookIds, List<AdventurePartyMember> party,
            String engineId, List<String> toolIds, long definitionVersion, long blueprintVersion,
            PlayabilityReport playabilityReport, ActiveSourceContext activeSourceContext) {
        return new RuntimeBinding(adventureId, ownerPlayerId, bindingVersion, scenarioPackageId, scenarioPackageRevision,
                rulebookIds, party, engineId, toolIds, definitionVersion, blueprintVersion, playabilityReport, activeSourceContext);
    }
    public static RuntimeBinding rehydrate(AdventureId adventureId, OwnerPlayerId ownerPlayerId, long bindingVersion,
            UUID scenarioPackageId, long scenarioPackageRevision, List<UUID> rulebookIds, List<AdventurePartyMember> party,
            String engineId, List<String> toolIds, long definitionVersion, long blueprintVersion,
            PlayabilityReport playabilityReport, ActiveSourceContext activeSourceContext, RuntimeReadiness readiness) {
        return new RuntimeBinding(adventureId, ownerPlayerId, bindingVersion, scenarioPackageId, scenarioPackageRevision,
                rulebookIds, party, engineId, toolIds, definitionVersion, blueprintVersion, playabilityReport, activeSourceContext, readiness);
    }
    public RuntimeBinding withNewPackage(
            UUID scenarioPackageId,
            long scenarioPackageRevision,
            PlayabilityReport playabilityReport,
            ActiveSourceContext activeSourceContext) {
        return new RuntimeBinding(
                adventureId, ownerPlayerId, bindingVersion + 1, scenarioPackageId, scenarioPackageRevision,
                rulebookIds, party, engineId, toolIds, gameSystemDefinitionVersion, characterBlueprintVersion, playabilityReport, activeSourceContext);
    }

    public RuntimeBinding withActiveSourceContext(PlayabilityReport playabilityReport, ActiveSourceContext activeSourceContext) {
        return new RuntimeBinding(
                adventureId, ownerPlayerId, bindingVersion, scenarioPackageId, scenarioPackageRevision,
                rulebookIds, party, engineId, toolIds, gameSystemDefinitionVersion, characterBlueprintVersion, playabilityReport, activeSourceContext);
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
    public long gameSystemDefinitionVersion() { return gameSystemDefinitionVersion; }
    public long characterBlueprintVersion() { return characterBlueprintVersion; }
    public PlayabilityReport playabilityReport() { return playabilityReport; }
    public ActiveSourceContext activeSourceContext() { return activeSourceContext; }
    public RuntimeReadiness readiness() { return readiness; }

    public RuntimeBinding withSelection(ActiveSourceContext selected, PlayabilityReport playabilityReport) {
        return new RuntimeBinding(
                adventureId, ownerPlayerId, bindingVersion, scenarioPackageId, scenarioPackageRevision,
                rulebookIds, party, engineId, toolIds, gameSystemDefinitionVersion, characterBlueprintVersion, playabilityReport, selected);
    }

    public RuntimeBinding withReadiness(RuntimeReadiness readiness) {
        return new RuntimeBinding(adventureId, ownerPlayerId, bindingVersion, scenarioPackageId, scenarioPackageRevision,
                rulebookIds, party, engineId, toolIds, gameSystemDefinitionVersion, characterBlueprintVersion,
                playabilityReport, activeSourceContext, readiness);
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    private static RuntimeReadiness readiness(long bindingVersion, PlayabilityReport report) {
        RuntimeReadinessStatus status = switch (report.status()) {
            case BLOCKED -> RuntimeReadinessStatus.BLOCKED;
            case PLAYABLE_WITH_LIMITS -> RuntimeReadinessStatus.SUPPORTED_DEGRADED;
            case PLAYABLE -> RuntimeReadinessStatus.INDEXED_READY;
        };
        return new RuntimeReadiness(bindingVersion, status, report.blockers(), report.warnings(),
                status == RuntimeReadinessStatus.BLOCKED);
    }
}
