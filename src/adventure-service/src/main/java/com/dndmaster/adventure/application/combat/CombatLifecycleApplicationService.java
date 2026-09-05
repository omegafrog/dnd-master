package com.dndmaster.adventure.application.combat;

import com.dndmaster.adventure.domain.combat.*;
import java.util.List;
import java.util.UUID;
import com.dndmaster.adventure.domain.runtime.GmTurn;
import com.dndmaster.adventure.domain.runtime.GmTurnStatus;

/** Owns the local combat-entry transaction seam; later actions do not belong here. */
public final class CombatLifecycleApplicationService {
    private final CombatEncounterRepository repository;
    private final CombatEventRepository eventRepository;
    public CombatLifecycleApplicationService(CombatEncounterRepository repository) { this.repository = repository; this.eventRepository = null; }
    public CombatLifecycleApplicationService(CombatEncounterRepository repository, CombatEventRepository eventRepository) {
        this.repository = repository; this.eventRepository = eventRepository;
    }
    public CombatEncounter startFromCommittedGmTurn(UUID adventureId, boolean gmTurnCommitted,
                                                    List<CombatParticipant> participants) {
        CombatStartPolicy.requireNoActiveEncounter(adventureId,
                repository.findActive(adventureId).stream().toList());
        return repository.save(CombatStartPolicy.startFromCommittedGmTurn(gmTurnCommitted, adventureId, participants));
    }

    public CombatEncounter startFromCommittedGmTurn(UUID adventureId, GmTurn gmTurn, CombatStartProposal proposal) {
        if (gmTurn == null || gmTurn.status() != GmTurnStatus.COMMITTED) {
            throw new IllegalStateException("combat requires a committed GM turn");
        }
        if (proposal == null || !proposal.accepted()) return null;
        CombatStartPolicy.requireNoActiveEncounter(adventureId,
                repository.findActive(adventureId).stream().toList());
        CombatEncounter encounter = CombatStartPolicy.startFromCommittedGmTurn(true, adventureId, proposal.participants())
                .withEventCursor(1);
        CombatEncounter saved = repository.save(encounter);
        if (eventRepository != null) {
            eventRepository.append(new CombatEvent(saved.encounterId(), 1, "COMBAT_STARTED",
                    "{\"encounterId\":\"" + saved.encounterId() + "\",\"round\":1,\"currentParticipantId\":\""
                            + saved.currentParticipantId() + "\"}"));
        }
        return saved;
    }
}
