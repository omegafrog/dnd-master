package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import com.dndmaster.adventure.application.session.AdventureSessionRepository;
import com.dndmaster.adventure.application.storyplan.AdventureStoryPlanApplicationService;
import com.dndmaster.adventure.application.storyplan.AdventureStoryPlanRepository;
import com.dndmaster.adventure.application.storyplan.AdventureStoryPlanGenerationPort;
import com.dndmaster.adventure.application.runtime.GmProviderBindingRepository;
import com.dndmaster.adventure.application.runtime.GmProviderSelection;
import com.dndmaster.adventure.application.runtime.ProviderBinding;
import com.dndmaster.adventure.domain.adventure.*;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
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
    void forwards_current_provider_selection_to_story_plan_generation() {
        var session = AdventureSession.create(SessionId.generate(), new OwnerPlayerId(UUID.randomUUID()), UUID.randomUUID(), 1, 1,
                new AdventureSessionRuntimeConfiguration(new ScenarioId(UUID.randomUUID()), new RuleSetId(UUID.randomUUID()), java.util.List.of(), "ollama", java.util.List.of("search"), "opening"));
        session.addPartyMember(new AdventurePartyMember(new CharacterSheetId(UUID.randomUUID()), ControlMode.DIRECT, false, false, false, false, false, false));
        var sessions = mock(AdventureSessionRepository.class);
        var plans = mock(AdventureStoryPlanRepository.class);
        var bindings = mock(GmProviderBindingRepository.class);
        var request = new AtomicReference<AdventureStoryPlanGenerationPort.Request>();
        when(sessions.findById(session.id())).thenReturn(Optional.of(session));
        when(plans.findBySessionId(session.id())).thenReturn(Optional.empty());
        when(bindings.current(session.id().value())).thenReturn(Optional.of(new ProviderBinding(session.id().value(),
                new GmProviderSelection("ollama", "qwen3:8b", "medium"), 1)));
        var generator = (AdventureStoryPlanGenerationPort) captured -> {
            request.set(captured);
            return java.util.List.of(new AdventureStoryPlanStage(1, "Opening", "Goal", "Conflict", "Transition", java.util.List.of(), java.util.List.of("ending-1")));
        };

        new AdventureStoryPlanApplicationService(plans, sessions, null, generator, bindings)
                .generate(session.id(), session.ownerPlayerId());

        assertEquals("ollama", request.get().provider());
        assertEquals("qwen3:8b", request.get().model());
        assertEquals("medium", request.get().reasoning());
    }
}
