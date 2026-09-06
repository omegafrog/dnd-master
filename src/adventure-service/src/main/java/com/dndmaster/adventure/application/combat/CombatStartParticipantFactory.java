package com.dndmaster.adventure.application.combat;

import com.dndmaster.adventure.domain.adventure.AdventurePartyMember;
import com.dndmaster.adventure.application.runtime.CombatEnemyProposal;
import com.dndmaster.adventure.domain.combat.CombatParticipant;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.UUID;

/** Builds the encounter roster from the committed party and GM-confirmed enemy identities. */
public final class CombatStartParticipantFactory {
    private CombatStartParticipantFactory() {}

    public static List<CombatParticipant> fromPartyAndGmProposal(UUID adventureId,
            List<AdventurePartyMember> party, List<CombatEnemyProposal> enemies) {
        return fromPartyAndGmProposal(adventureId, party, enemies,
                member -> member.characterSheetId().value().toString());
    }

    public static List<CombatParticipant> fromPartyAndGmProposal(UUID adventureId,
            List<AdventurePartyMember> party, List<CombatEnemyProposal> enemies,
            Function<AdventurePartyMember, String> displayNameFor) {
        if (enemies == null || enemies.isEmpty()) {
            throw new IllegalArgumentException("combat start requires at least one structured enemy");
        }
        List<CombatParticipant> participants = new ArrayList<>(party.stream()
                .map(member -> new CombatParticipant(member.characterSheetId().value(),
                        displayNameFor.apply(member),
                        member.controlMode() == com.dndmaster.adventure.domain.adventure.ControlMode.AGENT
                                ? CombatParticipant.Controller.AI
                                : CombatParticipant.Controller.PLAYER,
                        0, null))
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
                        CombatParticipant.Controller.AI, 0, "enemy",
                        com.dndmaster.adventure.domain.combat.TurnResources.initial(), enemy.statBlock()));
                index++;
            }
        }
        return List.copyOf(participants);
    }
}
