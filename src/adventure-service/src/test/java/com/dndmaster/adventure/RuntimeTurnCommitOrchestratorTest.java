package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dndmaster.adventure.application.runtime.InMemoryRuntimeTurnCommandRepository;
import com.dndmaster.adventure.application.runtime.InMemoryRuntimeTurnRepository;
import com.dndmaster.adventure.application.runtime.RuntimeTurn;
import com.dndmaster.adventure.application.runtime.RuntimeTurnCommand;
import com.dndmaster.adventure.application.runtime.RuntimeTurnCommandAdapter;
import com.dndmaster.adventure.application.runtime.RuntimeTurnCommandExecution;
import com.dndmaster.adventure.application.runtime.RuntimeTurnCommitOrchestrator;
import com.dndmaster.adventure.application.runtime.RuntimeTurnLifecycle;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RuntimeTurnCommitOrchestratorTest {
    @Test
    void executesCommandsInOrderAndSkipsDoneCommandsOnResume() {
        RuntimeTurnFixture fixture = new RuntimeTurnFixture();
        List<String> calls = new ArrayList<>();
        RuntimeTurnCommand first = fixture.command("character.update", 1, RuntimeTurnCommand.ExecutionStatus.DONE);
        RuntimeTurnCommand second = fixture.command("combat-map.update", 2, RuntimeTurnCommand.ExecutionStatus.PENDING);
        RuntimeTurnCommitOrchestrator orchestrator = fixture.orchestrator(command -> {
            calls.add(command.commandType());
            return RuntimeTurnCommandExecution.done("ok");
        });

        RuntimeTurnCommitOrchestrator.Result result = orchestrator.commit(
                fixture.readyTurn(), List.of(first, second), () -> calls.add("adventure"));

        assertEquals(RuntimeTurnCommitOrchestrator.Status.COMMITTED, result.status());
        assertEquals(List.of("combat-map.update", "adventure"), calls);
        assertEquals(RuntimeTurnLifecycle.COMMITTED, fixture.turns.findByTurnId(fixture.turnId).orElseThrow().lifecycle());
        assertEquals(RuntimeTurnCommand.ExecutionStatus.DONE,
                fixture.commands.findByTurnId(fixture.turnId).get(1).executionStatus());
    }

    @Test
    void transientFailureLeavesCommittingAndResumeContinuesFromFailedCommand() {
        RuntimeTurnFixture fixture = new RuntimeTurnFixture();
        List<String> calls = new ArrayList<>();
        RuntimeTurnCommand first = fixture.command("character.update", 1, RuntimeTurnCommand.ExecutionStatus.PENDING);
        RuntimeTurnCommand second = fixture.command("combat-map.update", 2, RuntimeTurnCommand.ExecutionStatus.PENDING);
        RuntimeTurnCommandAdapter adapter = new RuntimeTurnCommandAdapter() {
            private boolean failed;
            @Override public RuntimeTurnCommandExecution execute(RuntimeTurnCommand command) {
                calls.add(command.commandType());
                if (!failed) {
                    failed = true;
                    return RuntimeTurnCommandExecution.transientFailure("temporary outage");
                }
                return RuntimeTurnCommandExecution.done("ok");
            }
        };
        RuntimeTurnCommitOrchestrator orchestrator = fixture.orchestrator(adapter);

        RuntimeTurnCommitOrchestrator.Result firstAttempt = orchestrator.commit(
                fixture.readyTurn(), List.of(first, second), () -> calls.add("adventure"));
        RuntimeTurnCommitOrchestrator.Result resumed = orchestrator.resume(
                fixture.turnId, () -> calls.add("adventure"));

        assertEquals(RuntimeTurnCommitOrchestrator.Status.RETRY_REQUIRED, firstAttempt.status());
        assertEquals(RuntimeTurnCommitOrchestrator.Status.COMMITTED, resumed.status());
        assertEquals(List.of("character.update", "character.update", "combat-map.update", "adventure"), calls);
    }

    @Test
    void permanentFailureMarksRepairRequiredAndDoesNotCommitAdventure() {
        RuntimeTurnFixture fixture = new RuntimeTurnFixture();
        List<String> calls = new ArrayList<>();
        RuntimeTurnCommand command = fixture.command("combat-map.update", 1, RuntimeTurnCommand.ExecutionStatus.PENDING);
        RuntimeTurnCommitOrchestrator orchestrator = fixture.orchestrator(
                ignored -> RuntimeTurnCommandExecution.permanentFailure("invalid map token"));

        RuntimeTurnCommitOrchestrator.Result result = orchestrator.commit(
                fixture.readyTurn(), List.of(command), () -> calls.add("adventure"));

        assertEquals(RuntimeTurnCommitOrchestrator.Status.REPAIR_REQUIRED, result.status());
        assertEquals(List.of(), calls);
        assertEquals(RuntimeTurnLifecycle.COMMIT_REPAIR_REQUIRED,
                fixture.turns.findByTurnId(fixture.turnId).orElseThrow().lifecycle());
        RuntimeTurnCommitOrchestrator.Result resumed = fixture.orchestrator(
                ignored -> RuntimeTurnCommandExecution.done("must not run")).resume(
                        fixture.turnId, () -> calls.add("adventure"));
        assertEquals(RuntimeTurnCommitOrchestrator.Status.REPAIR_REQUIRED, resumed.status());
    }

    private static final class RuntimeTurnFixture {
        private final UUID turnId = UUID.randomUUID();
        private final InMemoryRuntimeTurnRepository turns = new InMemoryRuntimeTurnRepository();
        private final InMemoryRuntimeTurnCommandRepository commands = new InMemoryRuntimeTurnCommandRepository();

        RuntimeTurn readyTurn() {
            UUID commandId = UUID.randomUUID();
            UUID adventureId = UUID.randomUUID();
            UUID sessionId = UUID.randomUUID();
            UUID packageId = UUID.randomUUID();
            var evidence = new com.dndmaster.adventure.application.runtime.EvidencePack(List.of(), List.of(), List.of());
            var plan = new com.dndmaster.adventure.application.runtime.RuntimePlan(
                    "scene", "npc", "judgment", "narration", null, List.of(), List.of());
            var base = new RuntimeTurn(turnId, commandId, new com.dndmaster.adventure.domain.adventure.AdventureId(adventureId),
                    sessionId, packageId, 1, "action", evidence, plan, null,
                    new com.dndmaster.adventure.domain.adventure.AdventureContext("scene", "npc", "action", "judgment"),
                    List.of(), 0, List.of(), List.of(), false, false,
                    com.dndmaster.adventure.application.runtime.RuntimeTurnOrigin.GM, false);
            var pending = new com.dndmaster.adventure.application.runtime.PendingRuntimeState(
                    com.dndmaster.adventure.domain.runtime.GameStateDelta.empty(),
                    com.dndmaster.adventure.domain.runtime.DisclosureState.empty(),
                    com.dndmaster.adventure.domain.runtime.CurrentSituation.initial("problem"), List.of());
            return base.asRequested().beginResolving()
                    .fixResolution(new com.dndmaster.adventure.application.runtime.RuntimeTurnResolution("ok", null, List.of()), pending,
                            com.dndmaster.adventure.application.runtime.CompletionProposal.continueAdventure())
                    .beginNarration().beginSafetyCheck().readyToCommit("safe");
        }

        RuntimeTurnCommand command(String type, int order, RuntimeTurnCommand.ExecutionStatus status) {
            return RuntimeTurnCommand.create(turnId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                    "external", type, "{}", order).withStatus(status);
        }

        RuntimeTurnCommitOrchestrator orchestrator(RuntimeTurnCommandAdapter adapter) {
            return new RuntimeTurnCommitOrchestrator(turns, commands, adapter);
        }
    }
}
