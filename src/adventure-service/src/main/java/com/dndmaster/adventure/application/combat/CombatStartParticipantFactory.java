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
    private CombatStartParticipantFactory() {}

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

        int index = 0;
        for (CombatEnemyProposal enemy : enemies) {
            if (enemy == null || enemy.statBlock() == null) {
                throw new IllegalArgumentException("COMBAT_STAT_BLOCK_REQUIRED");
            }
            for (int instance = 1; instance <= enemy.count(); instance++) {
                String displayName = enemy.count() == 1 ? enemy.name() : enemy.name() + " " + instance;
                UUID enemyId = UUID.nameUUIDFromBytes(
                        (adventureId + "|combat-enemy|" + index + "|" + enemy.scenarioId() + "|" + instance)
                                .getBytes(StandardCharsets.UTF_8));
                participants.add(new CombatParticipant(enemyId, displayName,
                        CombatParticipant.Controller.AI, d20.getAsInt() + enemy.statBlock().initiativeModifier(), "enemy",
                        com.dndmaster.adventure.domain.combat.TurnResources.initial(), enemy.statBlock()));
                index++;
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
        int index = 0;
        for (CombatEnemyProposal enemy : enemies) {
            if (enemy == null || enemy.statBlock() == null) throw new IllegalArgumentException("COMBAT_STAT_BLOCK_REQUIRED");
            for (int instance = 1; instance <= enemy.count(); instance++) {
                String displayName = enemy.count() == 1 ? enemy.name() : enemy.name() + " " + instance;
                UUID id = UUID.nameUUIDFromBytes((adventureId + "|combat-enemy|" + index + "|" + enemy.scenarioId() + "|" + instance)
                        .getBytes(StandardCharsets.UTF_8));
                participants.add(new CombatParticipant(id, displayName, CombatParticipant.Controller.AI,
                        d20ForParticipant.apply(id, RollScope.ENEMY) + enemy.statBlock().initiativeModifier(), "enemy",
                        com.dndmaster.adventure.domain.combat.TurnResources.initial(), enemy.statBlock()));
                index++;
            }
        }
        return List.copyOf(participants);
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
