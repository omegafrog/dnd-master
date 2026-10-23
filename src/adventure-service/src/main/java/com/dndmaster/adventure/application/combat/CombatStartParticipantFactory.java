package com.dndmaster.adventure.application.combat;

import com.dndmaster.adventure.domain.adventure.AdventurePartyMember;
import com.dndmaster.adventure.application.runtime.CombatEnemyProposal;
import com.dndmaster.adventure.domain.combat.CombatParticipant;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.BiFunction;
import com.dndmaster.adventure.dice.domain.RollScope;
import java.util.UUID;

/** Builds the encounter roster from the committed party and GM-confirmed enemy identities. */
public final class CombatStartParticipantFactory {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(CombatStartParticipantFactory.class);
    private CombatStartParticipantFactory() {}

    public static List<CombatParticipant> applyExplorationAwareness(List<CombatParticipant> participants,
            com.dndmaster.adventure.domain.runtime.ExplorationCycle cycle,
            java.util.Map<UUID, UUID> hostileMapTokenToParticipantId) {
        long startedAt = System.nanoTime();
        if (com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.enabled()) LOGGER.info(
                "dev_exploration_awareness_transfer_begin participantCount={} cyclePresent={} crosswalkCount={}",
                participants == null ? 0 : participants.size(), cycle != null,
                hostileMapTokenToParticipantId == null ? 0 : hostileMapTokenToParticipantId.size());
        if (cycle == null) {
            if (com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.enabled()) LOGGER.info(
                    "dev_exploration_awareness_transfer_alternate branch=no_cycle result=unchanged durationMs={}",
                    elapsedMs(startedAt));
            return participants;
        }
        try {
            return resolveExplorationAwareness(participants, cycle, hostileMapTokenToParticipantId, startedAt);
        } catch (RuntimeException failure) {
            if (com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.enabled()) LOGGER.error(
                    "dev_exploration_awareness_transfer_failure participantCount={} crosswalkCount={} failureType={} durationMs={} recovery=reject_combat_start",
                    participants == null ? 0 : participants.size(),
                    hostileMapTokenToParticipantId == null ? 0 : hostileMapTokenToParticipantId.size(),
                    failure.getClass().getSimpleName(), elapsedMs(startedAt));
            throw failure;
        }
    }

    private static List<CombatParticipant> resolveExplorationAwareness(List<CombatParticipant> participants,
            com.dndmaster.adventure.domain.runtime.ExplorationCycle cycle,
            java.util.Map<UUID, UUID> hostileMapTokenToParticipantId, long startedAt) {
        Objects.requireNonNull(participants, "combat participants must not be null");
        Objects.requireNonNull(hostileMapTokenToParticipantId, "hostile map token crosswalk must not be null");
        if (!hasExplorationAwarenessEvidence(cycle)) {
            if (com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.enabled()) LOGGER.info(
                    "dev_exploration_awareness_transfer_alternate branch=no_awareness_evidence participantCount={} crosswalkCount={} durationMs={} result=unchanged",
                    participants.size(), hostileMapTokenToParticipantId.size(), elapsedMs(startedAt));
            return participants;
        }
        var currentHostileParticipantIds = participants.stream()
                .filter(participant -> participant.enemyKind() != null || participant.statBlock() != null)
                .map(CombatParticipant::participantId).collect(java.util.stream.Collectors.toSet());
        var currentMapTokenToParticipantId = new java.util.HashMap<UUID, UUID>();
        hostileMapTokenToParticipantId.forEach((tokenId, participantId) -> {
            if (tokenId != null && participantId != null && currentHostileParticipantIds.contains(participantId))
                currentMapTokenToParticipantId.put(tokenId, participantId);
        });
        var currentHostileTokenByParticipantId = new java.util.HashMap<UUID, UUID>();
        currentMapTokenToParticipantId.forEach((tokenId, participantId) ->
                currentHostileTokenByParticipantId.put(participantId, tokenId));
        int unmatchedCrosswalkCount = hostileMapTokenToParticipantId.size() - currentMapTokenToParticipantId.size();
        var durable = cycle.threatAwareness();
        var awareCharactersByParticipantId = new java.util.HashMap<UUID, java.util.Set<UUID>>();
        durable.awareCharactersByHostileTokenId().forEach((hostileId, characters) -> {
            UUID participantId = currentMapTokenToParticipantId.get(hostileId);
            if (participantId != null) awareCharactersByParticipantId.put(participantId, characters);
        });
        var awareHostileParticipantIds = durable.awareHostileTokenIds().stream()
                .map(currentMapTokenToParticipantId::get).filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        var event = cycle.pendingMovementEvent();
        if (event != null && !event.discoveredByCharacterSheetIds().isEmpty()) {
            for (UUID hostileId : event.threatHostileTokenIds()) {
                UUID participantId = currentMapTokenToParticipantId.get(hostileId);
                if (participantId != null) awareCharactersByParticipantId.merge(participantId,
                        event.awareCharactersByHostileTokenId().getOrDefault(hostileId, java.util.Set.of()),
                        (left, right) -> { var merged = new java.util.HashSet<>(left); merged.addAll(right); return java.util.Set.copyOf(merged); });
            }
            event.awareEnemyTokenIds().stream().map(currentMapTokenToParticipantId::get).filter(Objects::nonNull)
                    .forEach(awareHostileParticipantIds::add);
        }
        var proposal = cycle.pendingEnvironmentProposal();
        if (proposal != null && proposal.threatDiscovery() != null) {
            for (UUID hostileId : proposal.threatDiscovery().threatHostileTokenIds()) {
                UUID participantId = currentMapTokenToParticipantId.get(hostileId);
                if (participantId != null) awareCharactersByParticipantId.merge(participantId,
                        proposal.threatDiscovery().awareCharactersByHostileTokenId().getOrDefault(hostileId, java.util.Set.of()),
                        (left, right) -> { var merged = new java.util.HashSet<>(left); merged.addAll(right); return java.util.Set.copyOf(merged); });
            }
            proposal.threatDiscovery().awareEnemyTokenIds().stream().map(currentMapTokenToParticipantId::get)
                    .filter(Objects::nonNull).forEach(awareHostileParticipantIds::add);
        }
        List<CombatParticipant> result = participants.stream().map(participant -> {
            boolean hostile = participant.enemyKind() != null || participant.statBlock() != null;
            boolean isAware;
            if (hostile) {
                isAware = awareHostileParticipantIds.contains(participant.participantId());
            } else {
                isAware = !currentHostileParticipantIds.isEmpty() && currentHostileParticipantIds.stream()
                        .allMatch(hostileParticipantId -> {
                            UUID tokenId = currentHostileTokenByParticipantId.get(hostileParticipantId);
                            return tokenId != null && awareCharactersByParticipantId
                                    .getOrDefault(hostileParticipantId, java.util.Set.of()).contains(participant.participantId());
                        });
            }
            return participant.withSurprised(!isAware);
        }).toList();
        if (com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.enabled()) LOGGER.info(
                "dev_exploration_awareness_transfer_success participantCount={} hostileCount={} crosswalkCount={} unmatchedCrosswalkCount={} awareRelationshipCount={} awareParticipantCount={} surprisedParticipantCount={} durationMs={} result=applied",
                participants.size(), currentHostileParticipantIds.size(), currentMapTokenToParticipantId.size(),
                unmatchedCrosswalkCount, awareCharactersByParticipantId.values().stream().mapToInt(java.util.Set::size).sum()
                        + awareHostileParticipantIds.size(),
                result.size() - result.stream().filter(CombatParticipant::surprised).count(),
                result.stream().filter(CombatParticipant::surprised).count(), elapsedMs(startedAt));
        return result;
    }

    private static boolean hasExplorationAwarenessEvidence(
            com.dndmaster.adventure.domain.runtime.ExplorationCycle cycle) {
        var durable = cycle.threatAwareness();
        if (!durable.awareCharactersByHostileTokenId().isEmpty() || !durable.awareHostileTokenIds().isEmpty()) return true;
        var event = cycle.pendingMovementEvent();
        if (event != null && (!event.discoveredByCharacterSheetIds().isEmpty()
                || !event.awareCharacterSheetIds().isEmpty() || !event.awareEnemyTokenIds().isEmpty()
                || !event.awareCharactersByHostileTokenId().isEmpty() || event.enemiesAware())) return true;
        var proposal = cycle.pendingEnvironmentProposal();
        if (proposal == null) return false;
        if (!proposal.awareCharacterSheetIds().isEmpty()) return true;
        if (proposal.threatDiscovery() == null) return false;
        var discovery = proposal.threatDiscovery();
        return !discovery.discovererCharacterSheetIds().isEmpty() || !discovery.awareEnemyTokenIds().isEmpty()
                || !discovery.awareCharactersByHostileTokenId().isEmpty() || discovery.enemyDiscoveredFirst()
                || discovery.enemyHeardNotification() || discovery.bothSidesNotHidden();
    }

    private static long elapsedMs(long startedAt) { return (System.nanoTime() - startedAt) / 1_000_000; }

    /** Builds the server-owned map-token to combat-participant crosswalk for the current scenario enemies. */
    public static java.util.Map<UUID, UUID> hostileMapTokenToParticipantId(UUID adventureId,
            List<CombatEnemyProposal> enemies) {
        Objects.requireNonNull(adventureId, "adventure id must not be null");
        if (enemies == null) return java.util.Map.of();
        var result = new java.util.HashMap<UUID, UUID>();
        for (CombatEnemyProposal enemy : enemies) {
            if (enemy == null || enemy.mode() == com.dndmaster.adventure.application.runtime.CombatStartMode.INSTANT
                    || enemy.scenarioId().isBlank()) continue;
            for (int instance = 1; instance <= enemy.count(); instance++) {
                UUID participantId = combatEnemyId(adventureId, enemy.scenarioId(), instance);
                // Scenario map tokens and combat participants share this server-derived identity.
                result.put(participantId, participantId);
            }
        }
        return java.util.Map.copyOf(result);
    }

    public static List<CombatParticipant> fromPartyAndGmProposal(UUID adventureId,
            List<AdventurePartyMember> party, List<CombatEnemyProposal> enemies) {
        if (enemies == null || enemies.isEmpty()) throw new IllegalArgumentException("combat start requires at least one structured enemy");
        throw new IllegalStateException("combat start requires a command-aware initiative roller");
    }

    public static List<CombatParticipant> fromPartyAndGmProposal(UUID adventureId,
            List<AdventurePartyMember> party, List<CombatEnemyProposal> enemies,
            Function<AdventurePartyMember, String> displayNameFor) {
        if (enemies == null || enemies.isEmpty()) throw new IllegalArgumentException("combat start requires at least one structured enemy");
        throw new IllegalStateException("combat start requires a command-aware initiative roller");
    }

    public static List<CombatParticipant> fromPartyAndGmProposal(UUID adventureId,
            List<AdventurePartyMember> party, List<CombatEnemyProposal> enemies,
            Function<AdventurePartyMember, String> displayNameFor,
            Function<AdventurePartyMember, Integer> initiativeModifier,
            java.util.function.IntSupplier d20) {
        return fromPartyAndGmProposal(adventureId, party, enemies, displayNameFor, initiativeModifier,
                ignored -> 30, d20);
    }

    public static List<CombatParticipant> fromPartyAndGmProposal(UUID adventureId,
            List<AdventurePartyMember> party, List<CombatEnemyProposal> enemies,
            Function<AdventurePartyMember, String> displayNameFor,
            Function<AdventurePartyMember, Integer> initiativeModifier,
            Function<AdventurePartyMember, Integer> movementSpeed,
            java.util.function.IntSupplier d20) {
        if (enemies == null || enemies.isEmpty()) {
            throw new IllegalArgumentException("combat start requires at least one structured enemy");
        }
        List<CombatParticipant> participants = new ArrayList<>(party.stream()
                .map(member -> new CombatParticipant(member.characterSheetId().value(),
                        displayNameFor.apply(member),
                        member.controlMode() == com.dndmaster.adventure.domain.adventure.ControlMode.AGENT
                                ? CombatParticipant.Controller.AI
                                : CombatParticipant.Controller.PLAYER,
                        d20.getAsInt() + initiativeModifier.apply(member), null,
                        com.dndmaster.adventure.domain.combat.TurnResources.initial(
                                Objects.requireNonNull(movementSpeed.apply(member), "movement speed must not be null"))))
                .toList());

        for (CombatEnemyProposal enemy : enemies) {
            if (enemy == null || enemy.statBlock() == null) {
                throw new IllegalArgumentException("COMBAT_STAT_BLOCK_REQUIRED");
            }
            for (int instance = 1; instance <= enemy.count(); instance++) {
                String displayName = enemy.count() == 1 ? enemy.name() : enemy.name() + " " + instance;
                UUID enemyId = combatEnemyId(adventureId, enemy.scenarioId(), instance);
                participants.add(new CombatParticipant(enemyId, displayName,
                        CombatParticipant.Controller.AI, d20.getAsInt() + enemy.statBlock().initiativeModifier(), "enemy",
                        com.dndmaster.adventure.domain.combat.TurnResources.initial(), enemy.statBlock()));
            }
        }
        return List.copyOf(participants);
    }

    public static List<CombatParticipant> fromPartyAndGmProposal(UUID adventureId,
            List<AdventurePartyMember> party, List<CombatEnemyProposal> enemies,
            Function<AdventurePartyMember, String> displayNameFor,
            Function<AdventurePartyMember, Integer> initiativeModifier,
            Function<AdventurePartyMember, Integer> movementSpeed,
            BiFunction<UUID, RollScope, Integer> d20ForParticipant) {
        if (enemies == null || enemies.isEmpty()) throw new IllegalArgumentException("combat start requires at least one structured enemy");
        List<CombatParticipant> participants = new ArrayList<>();
        for (AdventurePartyMember member : party) {
            UUID id = member.characterSheetId().value();
            participants.add(new CombatParticipant(id, displayNameFor.apply(member),
                    member.controlMode() == com.dndmaster.adventure.domain.adventure.ControlMode.AGENT
                            ? CombatParticipant.Controller.AI : CombatParticipant.Controller.PLAYER,
                    d20ForParticipant.apply(id, member.controlMode() == com.dndmaster.adventure.domain.adventure.ControlMode.AGENT
                            ? RollScope.NPC : RollScope.PLAYER_ACTION) + initiativeModifier.apply(member), null,
                    com.dndmaster.adventure.domain.combat.TurnResources.initial(
                            Objects.requireNonNull(movementSpeed.apply(member), "movement speed must not be null"))));
        }
        for (CombatEnemyProposal enemy : enemies) {
            if (enemy == null || enemy.statBlock() == null) throw new IllegalArgumentException("COMBAT_STAT_BLOCK_REQUIRED");
            for (int instance = 1; instance <= enemy.count(); instance++) {
                String displayName = enemy.count() == 1 ? enemy.name() : enemy.name() + " " + instance;
                UUID id = combatEnemyId(adventureId, enemy.scenarioId(), instance);
                participants.add(new CombatParticipant(id, displayName, CombatParticipant.Controller.AI,
                        d20ForParticipant.apply(id, RollScope.ENEMY) + enemy.statBlock().initiativeModifier(), "enemy",
                        com.dndmaster.adventure.domain.combat.TurnResources.initial(), enemy.statBlock()));
            }
        }
        return List.copyOf(participants);
    }

    /** Stable across map preparation and combat activation for the same source scenario instance. */
    public static UUID combatEnemyId(UUID adventureId, String scenarioId, int instance) {
        if (adventureId == null || scenarioId == null || scenarioId.isBlank() || instance < 1)
            throw new IllegalArgumentException("combat enemy identity fields are required");
        return UUID.nameUUIDFromBytes((adventureId + "|combat-enemy|" + scenarioId + "|" + instance)
                .getBytes(StandardCharsets.UTF_8));
    }

    public static List<CombatParticipant> preparingFromPartyAndGmProposal(UUID adventureId,
            List<AdventurePartyMember> party, List<CombatEnemyProposal> enemies,
            Function<AdventurePartyMember, String> displayNameFor,
            Function<AdventurePartyMember, Integer> initiativeModifier,
            java.util.function.IntSupplier d20) {
        List<CombatParticipant> resolved = fromPartyAndGmProposal(adventureId, party, enemies,
                displayNameFor, initiativeModifier, d20);
        int partySize = party.size();
        List<CombatParticipant> pending = new ArrayList<>(resolved.subList(0, partySize));
        int participantIndex = partySize;
        for (CombatEnemyProposal enemy : enemies) {
            if (enemy.sheetIdentity() == null) throw new IllegalArgumentException("ENEMY_SHEET_IDENTITY_REQUIRED");
            for (int instance = 0; instance < enemy.count(); instance++) {
                CombatParticipant participant = resolved.get(participantIndex++);
                pending.add(new CombatParticipant(participant.participantId(), participant.displayName(), participant.controller(),
                        participant.initiative(), participant.publicCondition(), participant.resources(), null, null,
                        enemy.sheetIdentity().enemyKind()));
            }
        }
        return List.copyOf(pending);
    }

    public static List<CombatParticipant> preparingFromPartyAndGmProposal(UUID adventureId,
            List<AdventurePartyMember> party, List<CombatEnemyProposal> enemies,
            Function<AdventurePartyMember, String> displayNameFor,
            Function<AdventurePartyMember, Integer> initiativeModifier,
            Function<AdventurePartyMember, Integer> movementSpeed,
            BiFunction<UUID, RollScope, Integer> d20ForParticipant) {
        List<CombatParticipant> resolved = fromPartyAndGmProposal(adventureId, party, enemies,
                displayNameFor, initiativeModifier, movementSpeed, d20ForParticipant);
        List<CombatParticipant> pending = new ArrayList<>(resolved.subList(0, party.size()));
        int participantIndex = party.size();
        for (CombatEnemyProposal enemy : enemies) {
            if (enemy.sheetIdentity() == null) throw new IllegalArgumentException("ENEMY_SHEET_IDENTITY_REQUIRED");
            for (int instance = 0; instance < enemy.count(); instance++) {
                CombatParticipant participant = resolved.get(participantIndex++);
                pending.add(new CombatParticipant(participant.participantId(), participant.displayName(), participant.controller(),
                        participant.initiative(), participant.publicCondition(), participant.resources(), null, null,
                        enemy.sheetIdentity().enemyKind()));
            }
        }
        return List.copyOf(pending);
    }

    public static List<CombatParticipant> preparingFromPartyAndGmProposal(UUID adventureId,
            List<AdventurePartyMember> party, List<CombatEnemyProposal> enemies,
            Function<AdventurePartyMember, String> displayNameFor,
            Function<AdventurePartyMember, Integer> initiativeModifier,
            Function<AdventurePartyMember, Integer> movementSpeed,
            java.util.function.IntSupplier d20) {
        List<CombatParticipant> resolved = fromPartyAndGmProposal(adventureId, party, enemies,
                displayNameFor, initiativeModifier, movementSpeed, d20);
        int partySize = party.size();
        List<CombatParticipant> pending = new ArrayList<>(resolved.subList(0, partySize));
        int participantIndex = partySize;
        for (CombatEnemyProposal enemy : enemies) {
            if (enemy.sheetIdentity() == null) throw new IllegalArgumentException("ENEMY_SHEET_IDENTITY_REQUIRED");
            for (int instance = 0; instance < enemy.count(); instance++) {
                CombatParticipant participant = resolved.get(participantIndex++);
                pending.add(new CombatParticipant(participant.participantId(), participant.displayName(), participant.controller(),
                        participant.initiative(), participant.publicCondition(), participant.resources(), null, null,
                        enemy.sheetIdentity().enemyKind()));
            }
        }
        return List.copyOf(pending);
    }
}
