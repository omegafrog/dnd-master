package com.dndmaster.adventure.application.combat;

import com.dndmaster.adventure.domain.combat.*;
import java.util.List;
import java.util.UUID;
import com.dndmaster.adventure.domain.runtime.GmTurn;
import com.dndmaster.adventure.domain.runtime.GmTurnStatus;
import com.dndmaster.adventure.domain.adventure.Adventure;
import com.dndmaster.adventure.application.saved.AdventureRepository;

/** Owns the local combat-entry transaction seam; later actions do not belong here. */
public final class CombatLifecycleApplicationService {
    private final CombatEncounterRepository repository;
    private final CombatEventRepository eventRepository;
    private final AdventureRepository adventureRepository;
    private final CharacterCombatPort characterPort;
    private final CombatMapPort mapPort;
    private final CombatActionOperationRepository operationRepository;
    private final CombatWorkItemRepository workItemRepository;
    private final CombatEndGuard endGuard;
    public CombatLifecycleApplicationService(CombatEncounterRepository repository) {
        this.repository = repository; this.eventRepository = null; this.adventureRepository = null;
        this.characterPort = null; this.mapPort = null; this.operationRepository = null;
        this.workItemRepository = null; this.endGuard = new CombatEndGuard();
    }
    public CombatLifecycleApplicationService(CombatEncounterRepository repository, CombatEventRepository eventRepository) {
        this.repository = repository; this.eventRepository = eventRepository;
        this.adventureRepository = null; this.characterPort = null; this.mapPort = null;
        this.operationRepository = null; this.workItemRepository = null; this.endGuard = new CombatEndGuard();
    }
    public CombatLifecycleApplicationService(CombatEncounterRepository repository, CombatEventRepository eventRepository,
                                             AdventureRepository adventureRepository, CharacterCombatPort characterPort,
                                             CombatMapPort mapPort, CombatActionOperationRepository operationRepository,
                                             CombatWorkItemRepository workItemRepository) {
        this.repository = repository;
        this.eventRepository = eventRepository;
        this.adventureRepository = adventureRepository;
        this.characterPort = characterPort;
        this.mapPort = mapPort;
        this.operationRepository = operationRepository;
        this.workItemRepository = workItemRepository;
        this.endGuard = new CombatEndGuard();
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

    /**
     * Commits a typed GM end proposal. Foreign state is finalized through its
     * owning ports before the local encounter terminal event is appended.
     */
    public CombatEndResult endFromCommittedGmTurn(UUID adventureId, GmTurn gmTurn,
                                                   CombatEndProposal proposal) {
        if (gmTurn == null || gmTurn.status() != GmTurnStatus.COMMITTED) {
            throw new IllegalStateException("combat end requires a committed GM turn");
        }
        CombatEncounter encounter = repository.findActive(adventureId)
                .orElseThrow(() -> new CombatEndRejectedException("COMBAT_NOT_ACTIVE"));
        boolean pendingAction = operationRepository != null && operationRepository.hasPendingForEncounter(encounter.encounterId());
        boolean pendingWork = workItemRepository != null && workItemRepository.hasPendingForEncounter(encounter.encounterId());
        boolean externalEffectsCommitted = !pendingAction && !pendingWork;
        endGuard.requireAllowed(encounter, proposal, pendingAction, pendingWork, externalEffectsCommitted);

        if (adventureRepository != null) {
            Adventure adventure = adventureRepository.findById(
                    new com.dndmaster.adventure.domain.adventure.AdventureId(adventureId)).orElse(null);
            if (adventure != null) {
                CombatFinalizationCommand command = new CombatFinalizationCommand(adventureId, encounter.encounterId(),
                        adventure.sessionId().value(), adventure.ownerPlayerId().value(),
                        encounter.participants().stream().map(CombatParticipant::participantId).toList());
                if (characterPort != null) characterPort.commitFinalState(command);
                if (mapPort != null) mapPort.commitFinalState(command);
                adventure.commitCombatEnd(adventure.ownerPlayerId(), adventure.version(), proposal.summary());
                adventureRepository.save(adventure);
            }
        }
        CombatEncounter ended = encounter.end(proposal);
        repository.save(ended, encounter.version());
        if (eventRepository != null) eventRepository.append(PostCombatProjectionPolicy.endedEvent(proposal, ended.eventCursor()));
        return new CombatEndResult(PostCombatProjectionPolicy.summary(proposal, List.of()), ended.version());
    }
}
