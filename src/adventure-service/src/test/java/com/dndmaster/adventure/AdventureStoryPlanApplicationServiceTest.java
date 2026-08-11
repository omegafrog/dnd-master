package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import com.dndmaster.adventure.application.session.AdventureSessionRepository;
import com.dndmaster.adventure.application.storyplan.AdventureStoryPlanApplicationService;
import com.dndmaster.adventure.application.storyplan.AdventureStoryPlanRepository;
import com.dndmaster.adventure.application.storyplan.AdventureStoryPlanGenerationPort;
import com.dndmaster.adventure.domain.adventure.*;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdventureStoryPlanApplicationServiceTest {
    @Test
    void generates_plan_only_for_complete_party_and_captures_party_revision() {
        var session = AdventureSession.create(SessionId.generate(), new OwnerPlayerId(UUID.randomUUID()), UUID.randomUUID(), 3, 1,
                new AdventureSessionRuntimeConfiguration(new ScenarioId(UUID.randomUUID()), new RuleSetId(UUID.randomUUID()), java.util.List.of(), "ollama", java.util.List.of("search"), "opening"));
        session.addPartyMember(new AdventurePartyMember(new CharacterSheetId(UUID.randomUUID()), ControlMode.DIRECT, false, false, false, false, false, false));
        var sessions = mock(AdventureSessionRepository.class);
        var plans = mock(AdventureStoryPlanRepository.class);
        when(sessions.findById(session.id())).thenReturn(Optional.of(session));
        when(plans.findBySessionId(session.id())).thenReturn(Optional.empty());
        var service = new AdventureStoryPlanApplicationService(plans, sessions);

        var plan = service.generate(session.id(), session.ownerPlayerId());

        assertEquals(AdventureStoryPlanStatus.READY, plan.status());
        assertEquals(session.version(), plan.partyRevision());
        assertEquals(4, plan.stageCount());
        verify(plans).save(plan);
    }

    @Test
    void rejects_incomplete_party() {
        var session = AdventureSession.create(SessionId.generate(), new OwnerPlayerId(UUID.randomUUID()), UUID.randomUUID(), 1, 2,
                new AdventureSessionRuntimeConfiguration(new ScenarioId(UUID.randomUUID()), new RuleSetId(UUID.randomUUID()), java.util.List.of(), "ollama", java.util.List.of("search"), "opening"));
        var sessions = mock(AdventureSessionRepository.class);
        var plans = mock(AdventureStoryPlanRepository.class);
        when(sessions.findById(session.id())).thenReturn(Optional.of(session));

        assertThrows(IllegalStateException.class, () -> new AdventureStoryPlanApplicationService(plans, sessions).generate(session.id(), session.ownerPlayerId()));
        verify(plans, never()).save(any());
    }

    @Test
    void forwards_requested_length_and_ending_count_to_generator_and_plan() {
        var session = AdventureSession.create(SessionId.generate(), new OwnerPlayerId(UUID.randomUUID()), UUID.randomUUID(), 1, 1,
                new AdventureSessionRuntimeConfiguration(new ScenarioId(UUID.randomUUID()), new RuleSetId(UUID.randomUUID()), java.util.List.of(), "ollama", java.util.List.of("search"), "opening"));
        session.addPartyMember(new AdventurePartyMember(new CharacterSheetId(UUID.randomUUID()), ControlMode.DIRECT, false, false, false, false, false, false));
        var sessions = mock(AdventureSessionRepository.class);
        var plans = mock(AdventureStoryPlanRepository.class);
        var generator = mock(AdventureStoryPlanGenerationPort.class);
        when(sessions.findById(session.id())).thenReturn(Optional.of(session));
        when(plans.findBySessionId(session.id())).thenReturn(Optional.empty());
        when(generator.generate(any())).thenReturn(java.util.List.of(
                new AdventureStoryPlanStage(1, "One", "Goal", "Conflict", "Next", java.util.List.of(), java.util.List.of("ending-a")),
                new AdventureStoryPlanStage(2, "Two", "Goal", "Conflict", "Next", java.util.List.of(), java.util.List.of("ending-b")),
                new AdventureStoryPlanStage(3, "Three", "Goal", "Conflict", "Next", java.util.List.of(), java.util.List.of("ending-a")),
                new AdventureStoryPlanStage(4, "Four", "Goal", "Conflict", "Next", java.util.List.of(), java.util.List.of("ending-b")),
                new AdventureStoryPlanStage(5, "Five", "Goal", "Conflict", "Next", java.util.List.of(), java.util.List.of("ending-a")),
                new AdventureStoryPlanStage(6, "Six", "Goal", "Conflict", "Next", java.util.List.of(), java.util.List.of("ending-b")),
                new AdventureStoryPlanStage(7, "Seven", "Goal", "Conflict", "Next", java.util.List.of(), java.util.List.of("ending-a"))));

        var configuration = new AdventurePlanConfiguration(2, AdventureLength.LONG);
        var plan = new AdventureStoryPlanApplicationService(plans, sessions, null, generator)
                .generate(session.id(), session.ownerPlayerId(), configuration);

        var request = org.mockito.ArgumentCaptor.forClass(AdventureStoryPlanGenerationPort.Request.class);
        verify(generator).generate(request.capture());
        assertEquals(configuration, request.getValue().configuration());
        assertEquals(configuration, plan.configuration());
    }

    @Test
    void rejects_more_than_four_endings() {
        assertThrows(IllegalArgumentException.class, () -> new AdventurePlanConfiguration(5, AdventureLength.STANDARD));
    }

    @Test
    void compatibility_generator_honors_long_configuration() {
        var session = AdventureSession.create(SessionId.generate(), new OwnerPlayerId(UUID.randomUUID()), UUID.randomUUID(), 1, 1,
                new AdventureSessionRuntimeConfiguration(new ScenarioId(UUID.randomUUID()), new RuleSetId(UUID.randomUUID()), java.util.List.of(), "ollama", java.util.List.of("search"), "opening"));
        session.addPartyMember(new AdventurePartyMember(new CharacterSheetId(UUID.randomUUID()), ControlMode.DIRECT, false, false, false, false, false, false));
        var sessions = mock(AdventureSessionRepository.class);
        var plans = mock(AdventureStoryPlanRepository.class);
        when(sessions.findById(session.id())).thenReturn(Optional.of(session));
        when(plans.findBySessionId(session.id())).thenReturn(Optional.empty());

        var plan = new AdventureStoryPlanApplicationService(plans, sessions)
                .generate(session.id(), session.ownerPlayerId(), new AdventurePlanConfiguration(4, AdventureLength.LONG));

        assertEquals(7, plan.stageCount());
        assertEquals(4, plan.stages().stream().flatMap(stage -> stage.endingIds().stream()).distinct().count());
    }
}
