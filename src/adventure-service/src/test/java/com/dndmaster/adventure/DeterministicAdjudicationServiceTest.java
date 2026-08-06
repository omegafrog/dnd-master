package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.adventure.application.runtime.AuthoritativeResolution;
import com.dndmaster.adventure.application.runtime.DeterministicAdjudicationRequest;
import com.dndmaster.adventure.application.runtime.DeterministicAdjudicationService;
import com.dndmaster.adventure.application.runtime.InMemoryRuntimeCommandJournal;
import com.dndmaster.adventure.application.runtime.NarrationContract;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class DeterministicAdjudicationServiceTest {
    @Test
    void retries_with_same_input_return_same_authoritative_resolution_without_reexecution() {
        AtomicInteger executions = new AtomicInteger();
        DeterministicAdjudicationService service = new DeterministicAdjudicationService(request -> {
            executions.incrementAndGet();
            return AuthoritativeResolution.resolved(
                    "attack-hit", List.of("target.hp=-4"), List.of("rulebook:page-42", "roll:17"));
        });
        DeterministicAdjudicationRequest request = request("open-door", 7L);

        AuthoritativeResolution first = service.resolve(request);
        AuthoritativeResolution retry = service.resolve(request);

        assertEquals(first, retry);
        assertEquals(1, executions.get());
    }

    @Test
    void command_id_reuse_with_different_input_is_rejected() {
        DeterministicAdjudicationService service = new DeterministicAdjudicationService(request ->
                AuthoritativeResolution.resolved("accepted", List.of(), List.of("rulebook:page-1")));
        DeterministicAdjudicationRequest first = request("open-door", 7L);
        service.resolve(first);

        assertThrows(IllegalStateException.class, () -> service.resolve(
                new DeterministicAdjudicationRequest(first.commandId(), first.sessionId(), first.turnId(),
                        "attack-goblin", first.stateFingerprint(), first.seed(), first.expectedVersion())));
    }

    @Test
    void separate_service_instances_replay_from_shared_journal() {
        InMemoryRuntimeCommandJournal journal = new InMemoryRuntimeCommandJournal();
        AtomicInteger executions = new AtomicInteger();
        var resolver = (java.util.function.Function<DeterministicAdjudicationRequest, AuthoritativeResolution>) request -> {
            executions.incrementAndGet();
            return AuthoritativeResolution.resolved("accepted", List.of(), List.of("rules:1"));
        };
        DeterministicAdjudicationRequest request = request("open-door", 7L);

        new DeterministicAdjudicationService(journal, new com.fasterxml.jackson.databind.ObjectMapper(), resolver)
                .resolve(request);
        AuthoritativeResolution retry = new DeterministicAdjudicationService(
                journal, new com.fasterxml.jackson.databind.ObjectMapper(), resolver).resolve(request);

        assertEquals("accepted", retry.outcome());
        assertEquals(1, executions.get());
    }

    @Test
    void narration_contract_accepts_only_resolved_outcome_and_keeps_provenance() {
        AuthoritativeResolution resolution = AuthoritativeResolution.resolved(
                "attack-hit", List.of("target.hp=-4"), List.of("rulebook:page-42", "roll:17"));

        NarrationContract contract = NarrationContract.from(resolution, "The blow lands.");

        assertEquals("attack-hit", contract.resolution().outcome());
        assertEquals(List.of("rulebook:page-42", "roll:17"), contract.resolution().provenance());
        assertEquals("The blow lands.", contract.narration());
    }

    @Test
    void unresolved_outcome_cannot_be_narrated() {
        AuthoritativeResolution pending = AuthoritativeResolution.pending("needs a roll", List.of("rulebook:page-42"));

        assertThrows(IllegalStateException.class, () -> NarrationContract.from(pending, "You hit."));
    }

    private static DeterministicAdjudicationRequest request(String action, long seed) {
        return new DeterministicAdjudicationRequest(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), action,
                "scene=hall;hero.hp=10", seed, 0);
    }
}
