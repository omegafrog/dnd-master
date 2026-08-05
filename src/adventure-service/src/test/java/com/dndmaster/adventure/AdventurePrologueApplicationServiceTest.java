package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import com.dndmaster.adventure.application.prologue.*;
import com.dndmaster.adventure.application.runtime.CharacterSheetReadPort;
import com.dndmaster.adventure.application.saved.AdventureRepository;
import com.dndmaster.adventure.application.storyplan.AdventureStoryPlanRepository;
import com.dndmaster.adventure.domain.adventure.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdventurePrologueApplicationServiceTest {
    @Test
    void creates_one_grounded_prologue_from_current_stage_and_sheet_snapshot() {
        var owner = new OwnerPlayerId(UUID.randomUUID());
        var sheetId = new CharacterSheetId(UUID.randomUUID());
        var adventure = Adventure.create(new AdventureId(UUID.randomUUID()), SessionId.generate(), owner,
                new ScenarioId(UUID.randomUUID()), new RuleSetId(UUID.randomUUID()),
                List.of(new AdventurePartyMember(sheetId, ControlMode.DIRECT, false, false, false, false, false, false)),
                new AdventureContext("opening", null, null, null));
        var stage = new AdventureStoryPlanStage(1, "The Bell", "Find the bell", "A warning sounds", "Reach the tower", List.of("bell"), List.of("safe"));
        var plan = AdventureStoryPlan.ready(adventure.sessionId(), 0, 1, List.of(stage));
        var adventures = mock(AdventureRepository.class);
        var plans = mock(AdventureStoryPlanRepository.class);
        var sheets = mock(CharacterSheetReadPort.class);
        var generator = mock(AdventurePrologueGenerationPort.class);
        when(adventures.findById(adventure.id())).thenReturn(Optional.of(adventure));
        when(plans.findBySessionId(adventure.sessionId())).thenReturn(Optional.of(plan));
        when(sheets.read(sheetId)).thenReturn(new CharacterSheetReadPort.CharacterSheet(sheetId, "Mira", 2));
        when(generator.generate(any())).thenReturn("Mira hears the bell. The warning sounds from the tower.");

        new AdventurePrologueApplicationService(adventures, plans, sheets, generator).ensure(adventure.id(), owner);

        assertEquals(List.of(new ConversationEntry(0, "AI_GAME_MASTER", "Mira hears the bell. The warning sounds from the tower.")), adventure.conversation());
        verify(adventures).save(adventure);
        new AdventurePrologueApplicationService(adventures, plans, sheets, generator).ensure(adventure.id(), owner);
        verify(generator, times(1)).generate(any());
    }
}
