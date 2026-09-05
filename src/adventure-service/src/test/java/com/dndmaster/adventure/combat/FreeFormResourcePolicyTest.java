package com.dndmaster.adventure.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.adventure.application.combat.AiCombatDecisionPort;
import com.dndmaster.adventure.application.combat.AiCombatDecisionPortAdapter;
import com.dndmaster.adventure.application.combat.CharacterCombatPort;
import com.dndmaster.adventure.application.combat.CombatActionApplicationService;
import com.dndmaster.adventure.application.combat.CombatActionCommand;
import com.dndmaster.adventure.application.combat.CombatActionOperation;
import com.dndmaster.adventure.application.combat.CombatActionOperationRepository;
import com.dndmaster.adventure.application.combat.CombatActionResponse;
import com.dndmaster.adventure.application.combat.CombatActorRole;
import com.dndmaster.adventure.application.combat.CombatEncounterRepository;
import com.dndmaster.adventure.application.combat.CombatEventRepository;
import com.dndmaster.adventure.application.combat.CombatOutcome;
import com.dndmaster.adventure.application.combat.DiceCombatPort;
import com.dndmaster.adventure.application.combat.FreeFormCombatCommand;
import com.dndmaster.adventure.application.combat.FreeFormCombatContext;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.CharacterSheetId;
import com.dndmaster.adventure.domain.adventure.RuleSetId;
import com.dndmaster.adventure.domain.combat.CombatEffectProposal;
import com.dndmaster.adventure.domain.combat.CombatEncounter;
import com.dndmaster.adventure.domain.combat.CombatEvent;
import com.dndmaster.adventure.domain.combat.CombatParticipant;
import com.dndmaster.adventure.domain.combat.CombatStartPolicy;
import com.dndmaster.adventure.domain.combat.FreeFormActionPlan;
import com.dndmaster.adventure.domain.combat.TurnResourceCost;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FreeFormResourcePolicyTest {
    @Test
    void interpreted_cost_is_reserved_and_committed_once_through_the_standard_operation() {
        UUID adventureId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        Fixture fixture = fixture(adventureId, actorId, targetId);
        TurnResourceCost interpretedCost = new TurnResourceCost(10, false, true, false);
        FreeFormActionPlan plan = new FreeFormActionPlan(actorId, targetId, interpretedCost,
                false, null, null, CombatEffectProposal.none(), "The ham lands.", "The ham lands.");
        fixture.decisionPort.delegate = new AiCombatDecisionPortAdapter(context -> plan);
        FreeFormCombatCommand command = command(adventureId, actorId, "throw ham at the goblin", 1);

        CombatActionResponse response = fixture.service.submitFreeForm(command);
        CombatActionResponse retry = fixture.service.submitFreeForm(command);

        assertEquals(response, retry);
        assertEquals(20, fixture.encounters.value.currentParticipant().resources().movement());
        assertEquals(true, fixture.encounters.value.currentParticipant().resources().actionAvailable());
        assertEquals(false, fixture.encounters.value.currentParticipant().resources().bonusActionAvailable());
        assertEquals(0, fixture.calls.dice);
        assertEquals(1, fixture.calls.characterMutations);
        assertEquals(1, fixture.operations.values.size());
        assertEquals(1, fixture.events.values.stream().filter(e -> e.eventType().equals("ACTION_RESOLVED")).count());
    }

    @Test
    void invalid_interpreted_proposal_does_not_create_an_operation_or_mutate_resources() {
        UUID adventureId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        Fixture fixture = fixture(adventureId, actorId, UUID.randomUUID());
        fixture.decisionPort.delegate = new AiCombatDecisionPortAdapter(context -> new FreeFormActionPlan(
                actorId, UUID.randomUUID(), TurnResourceCost.actionOnly(), false, null, null,
                CombatEffectProposal.none(), "invalid", "invalid"));

        assertThrows(RuntimeException.class, () -> fixture.service.submitFreeForm(
                command(adventureId, actorId, "throw ham at the goblin", 1)));
        assertEquals(true, fixture.encounters.value.currentParticipant().resources().actionAvailable());
        assertEquals(0, fixture.operations.values.size());
        assertEquals(0, fixture.calls.characterMutations);
    }

    private static FreeFormCombatCommand command(UUID adventureId, UUID actorId, String text, long version) {
        AdventureId id = new AdventureId(adventureId);
        CombatActionCommand base = new CombatActionCommand(UUID.randomUUID(), id, id.value(),
                new RuleSetId(UUID.randomUUID()), new CharacterSheetId(actorId), null, CombatActorRole.PLAYER,
                "FREE_FORM", null, UUID.randomUUID(), actorId, version, null, null, null, null, false);
        return new FreeFormCombatCommand(base, text);
    }

    private static Fixture fixture(UUID adventureId, UUID actorId, UUID targetId) {
        EncounterStore encounters = new EncounterStore(CombatStartPolicy.startFromCommittedGmTurn(true, adventureId,
                List.of(new CombatParticipant(actorId, "Hero", CombatParticipant.Controller.PLAYER, 15, "healthy"),
                        new CombatParticipant(targetId, "Goblin", CombatParticipant.Controller.AI, 10, null))));
        OperationStore operations = new OperationStore();
        EventStore events = new EventStore();
        Calls calls = new Calls();
        DecisionHolder decision = new DecisionHolder(context ->
                FreeFormActionPlan.narrativeOnly(context.declaration().actorId(), TurnResourceCost.actionOnly(),
                        "ok", "ok"));
        CombatActionApplicationService service = new CombatActionApplicationService(encounters, operations, events,
                new com.dndmaster.adventure.domain.combat.CombatRulesEngine(), command -> { calls.dice++; return 18; },
                new CharacterCombatPort() {
                    @Override public void requireUsableCharacter(CombatActionCommand ignored) { }
                    @Override public void applyOutcome(CombatActionCommand ignored, CombatOutcome ignoredOutcome) {
                        calls.characterMutations++;
                    }
                }, new com.dndmaster.adventure.application.combat.AiCombatPort() {
                    @Override public void controlState(CombatActionCommand ignored) { }
                    @Override public String adjudicate(CombatActionCommand ignored, int ignoredDice) { return "ok"; }
                }, command -> { }, decision);
        return new Fixture(service, encounters, operations, events, calls, decision);
    }

    private static final class Fixture {
        private final CombatActionApplicationService service;
        private final EncounterStore encounters;
        private final OperationStore operations;
        private final EventStore events;
        private final Calls calls;
        private final DecisionHolder decisionPort;

        private Fixture(CombatActionApplicationService service, EncounterStore encounters,
                        OperationStore operations, EventStore events, Calls calls,
                        DecisionHolder decisionPort) {
            this.service = service;
            this.encounters = encounters;
            this.operations = operations;
            this.events = events;
            this.calls = calls;
            this.decisionPort = decisionPort;
        }
    }

    private static final class EncounterStore implements CombatEncounterRepository {
        private CombatEncounter value;
        private EncounterStore(CombatEncounter value) { this.value = value; }
        @Override public Optional<CombatEncounter> findActive(UUID ignored) { return Optional.of(value); }
        @Override public CombatEncounter save(CombatEncounter encounter) { value = encounter; return encounter; }
    }
    private static final class OperationStore implements CombatActionOperationRepository {
        private final Map<UUID, CombatActionOperation> values = new HashMap<>();
        @Override public Optional<CombatActionOperation> findByCommandId(UUID id) { return Optional.ofNullable(values.get(id)); }
        @Override public void save(CombatActionOperation operation) { values.put(operation.commandId(), operation); }
    }
    private static final class EventStore implements CombatEventRepository {
        private final List<CombatEvent> values = new ArrayList<>();
        @Override public void append(CombatEvent event) { values.add(event); }
        @Override public List<CombatEvent> after(UUID ignored, long sequence) { return List.of(); }
    }
    private static final class Calls {
        private int dice;
        private int characterMutations;
    }

    private static final class DecisionHolder implements AiCombatDecisionPort {
        private AiCombatDecisionPort delegate;
        private DecisionHolder(AiCombatDecisionPort delegate) { this.delegate = delegate; }
        @Override public FreeFormActionPlan interpretFreeForm(FreeFormCombatContext context) {
            return delegate.interpretFreeForm(context);
        }
    }
}
