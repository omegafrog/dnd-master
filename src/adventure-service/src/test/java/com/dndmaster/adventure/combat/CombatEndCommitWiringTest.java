package com.dndmaster.adventure.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.adventure.application.combat.CharacterCombatPort;
import com.dndmaster.adventure.application.combat.CombatActionOperation;
import com.dndmaster.adventure.application.combat.CombatActionOperationRepository;
import com.dndmaster.adventure.application.combat.CombatEncounterRepository;
import com.dndmaster.adventure.application.combat.CombatEventRepository;
import com.dndmaster.adventure.application.combat.CombatFinalizationCommand;
import com.dndmaster.adventure.application.combat.CombatLifecycleApplicationService;
import com.dndmaster.adventure.application.combat.CombatMapPort;
import com.dndmaster.adventure.application.combat.CombatWorkItem;
import com.dndmaster.adventure.application.combat.CombatWorkItemRepository;
import com.dndmaster.adventure.application.saved.AdventureRepository;
import com.dndmaster.adventure.domain.adventure.Adventure;
import com.dndmaster.adventure.domain.adventure.AdventureContext;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.CharacterSheetId;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.adventure.RuleSetId;
import com.dndmaster.adventure.domain.adventure.ScenarioId;
import com.dndmaster.adventure.domain.adventure.SessionId;
import com.dndmaster.adventure.domain.combat.CombatEndProposal;
import com.dndmaster.adventure.domain.combat.CombatEncounter;
import com.dndmaster.adventure.domain.combat.CombatParticipant;
import com.dndmaster.adventure.domain.runtime.GmInput;
import com.dndmaster.adventure.domain.runtime.GmTurn;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CombatEndCommitWiringTest {
    @Test
    void committed_end_finalizes_owner_boundaries_and_removes_active_encounter() {
        UUID adventureId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID characterId = UUID.randomUUID();
        var encounterRepository = new EncounterStore();
        var eventRepository = new EventStore();
        var adventure = Adventure.create(new AdventureId(adventureId), new SessionId(UUID.randomUUID()),
                new OwnerPlayerId(ownerId), new ScenarioId(UUID.randomUUID()), new RuleSetId(UUID.randomUUID()),
                new CharacterSheetId(characterId), new AdventureContext("전투 장면", "적대적", "공격", null));
        var adventureRepository = new AdventureStore(adventure);
        var characterPort = new RecordingCharacterPort();
        var mapPort = new RecordingMapPort();
        var service = new CombatLifecycleApplicationService(encounterRepository, eventRepository, adventureRepository,
                characterPort, mapPort, new EmptyOperationStore(), new EmptyWorkStore());
        var participant = new CombatParticipant(characterId, "영웅", CombatParticipant.Controller.PLAYER, 12, null);
        encounterRepository.active = new CombatEncounter(UUID.randomUUID(), adventureId,
                CombatEncounter.Status.ACTIVE, 1, characterId, List.of(participant), 4, 7);
        var proposal = CombatEndProposal.gm(adventureId, encounterRepository.active.encounterId(),
                CombatEndProposal.Reason.ENEMIES_DEFEATED, "적을 물리치고 안전을 확보했습니다.");

        var result = service.endFromCommittedGmTurn(adventureId, committedTurn(), proposal);

        assertEquals(CombatEncounter.Status.ENDED, encounterRepository.saved.status());
        assertFalse(encounterRepository.findActive(adventureId).isPresent());
        assertEquals("COMBAT_ENDED", eventRepository.events.getFirst().eventType());
        assertEquals(1, characterPort.commands.size());
        assertEquals(1, mapPort.commands.size());
        assertEquals(1, adventure.version());
        assertEquals(proposal.summary(), adventure.currentContext().latestJudgment());
        assertEquals(5, result.encounterVersion());
    }

    @Test
    void pending_durable_work_rejects_terminal_commit_and_leaves_active_state_intact() {
        UUID adventureId = UUID.randomUUID();
        var encounterRepository = new EncounterStore();
        encounterRepository.active = new CombatEncounter(UUID.randomUUID(), adventureId,
                CombatEncounter.Status.ACTIVE, 1, UUID.randomUUID(), List.of(
                        new CombatParticipant(UUID.randomUUID(), "영웅", CombatParticipant.Controller.PLAYER, 12, null)), 1, 1);
        var proposal = CombatEndProposal.gm(adventureId, encounterRepository.active.encounterId(),
                CombatEndProposal.Reason.SURRENDER, "전투를 중단합니다.");
        var service = new CombatLifecycleApplicationService(encounterRepository, new EventStore(), null, null, null,
                new PendingOperationStore(), new EmptyWorkStore());

        assertThrows(IllegalStateException.class, () -> service.endFromCommittedGmTurn(adventureId, committedTurn(), proposal));
        assertEquals(CombatEncounter.Status.ACTIVE, encounterRepository.active.status());
    }

    private static GmTurn committedTurn() {
        return GmTurn.start(UUID.randomUUID(), UUID.randomUUID(), 0,
                new GmInput.TextInput("combat end")).process().commit("provider");
    }

    private static final class EncounterStore implements CombatEncounterRepository {
        private CombatEncounter active;
        private CombatEncounter saved;
        @Override public Optional<CombatEncounter> findActive(UUID adventureId) {
            return active != null && active.adventureId().equals(adventureId) && active.status() != CombatEncounter.Status.ENDED
                    ? Optional.of(active) : Optional.empty();
        }
        @Override public CombatEncounter save(CombatEncounter encounter) { saved = encounter; active = encounter; return encounter; }
        @Override public CombatEncounter save(CombatEncounter encounter, long expectedVersion) {
            assertEquals(expectedVersion, active.version());
            return save(encounter);
        }
    }

    private static final class EventStore implements CombatEventRepository {
        private final List<com.dndmaster.adventure.domain.combat.CombatEvent> events = new ArrayList<>();
        @Override public void append(com.dndmaster.adventure.domain.combat.CombatEvent event) { events.add(event); }
        @Override public List<com.dndmaster.adventure.domain.combat.CombatEvent> after(UUID encounterId, long sequence) {
            return events.stream().filter(event -> event.encounterId().equals(encounterId) && event.sequence() > sequence).toList();
        }
    }

    private static final class AdventureStore implements AdventureRepository {
        private final Adventure adventure;
        private AdventureStore(Adventure adventure) { this.adventure = adventure; }
        @Override public Optional<Adventure> findById(AdventureId id) { return Optional.of(adventure); }
        @Override public List<Adventure> findSavedByOwner(OwnerPlayerId owner) { return List.of(adventure); }
        @Override public void save(Adventure value) { assertEquals(adventure, value); }
    }

    private static final class RecordingCharacterPort implements CharacterCombatPort {
        private final List<CombatFinalizationCommand> commands = new ArrayList<>();
        @Override public void requireUsableCharacter(com.dndmaster.adventure.application.combat.CombatActionCommand command) {}
        @Override public void commitFinalState(CombatFinalizationCommand command) { commands.add(command); }
    }

    private static final class RecordingMapPort implements CombatMapPort {
        private final List<CombatFinalizationCommand> commands = new ArrayList<>();
        @Override public void validateAndMove(com.dndmaster.adventure.application.combat.CombatActionCommand command) {}
        @Override public void commitFinalState(CombatFinalizationCommand command) { commands.add(command); }
    }

    private static class EmptyOperationStore implements CombatActionOperationRepository {
        @Override public Optional<CombatActionOperation> findByCommandId(UUID commandId) { return Optional.empty(); }
        @Override public void save(CombatActionOperation operation) {}
    }
    private static final class PendingOperationStore extends EmptyOperationStore {
        @Override public boolean hasPendingForEncounter(UUID encounterId) { return true; }
    }
    private static final class EmptyWorkStore implements CombatWorkItemRepository {
        @Override public void enqueue(CombatWorkItem workItem) {}
        @Override public Optional<CombatWorkItem> claim(String workerId, Duration lease, Instant now) { return Optional.empty(); }
        @Override public void save(CombatWorkItem workItem) {}
        @Override public Optional<CombatWorkItem> findByOperationId(UUID operationId) { return Optional.empty(); }
    }
}
