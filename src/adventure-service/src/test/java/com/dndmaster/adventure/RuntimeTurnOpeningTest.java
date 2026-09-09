package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dndmaster.adventure.application.knowledge.SessionKnowledgeSetRepository;
import com.dndmaster.adventure.application.runtime.NarrationSafetyPort;
import com.dndmaster.adventure.application.runtime.RuntimeBindingRepository;
import com.dndmaster.adventure.application.runtime.RuntimeEvidenceSearchPort;
import com.dndmaster.adventure.application.runtime.RuntimePlanningPort;
import com.dndmaster.adventure.application.runtime.RuntimeTurnApplicationService;
import com.dndmaster.adventure.application.runtime.RuntimeTurnRepository;
import com.dndmaster.adventure.application.runtime.RuntimeTurnResult;
import com.dndmaster.adventure.application.runtime.SubmitRuntimeTurnCommand;
import com.dndmaster.adventure.application.saved.AdventureRepository;
import com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageRepository;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RuntimeTurnOpeningTest {
    @Test
    void sends_opening_through_the_normal_turn_command_shape() {
        RuntimeTurnRepository turns = mock(RuntimeTurnRepository.class);
        when(turns.findByCommandId(any())).thenReturn(Optional.empty());
        RuntimeTurnApplicationService service = spy(new RuntimeTurnApplicationService(
                mock(AdventureRepository.class), mock(RuntimeBindingRepository.class), mock(ScenarioPackageRepository.class),
                turns, mock(RuntimeEvidenceSearchPort.class), mock(RuntimePlanningPort.class),
                mock(NarrationSafetyPort.class), mock(SessionKnowledgeSetRepository.class)));
        RuntimeTurnResult expected = mock(RuntimeTurnResult.class);
        doReturn(expected).when(service).submitTurn(any(SubmitRuntimeTurnCommand.class));
        AdventureId adventureId = AdventureId.generate();
        OwnerPlayerId owner = new OwnerPlayerId(UUID.randomUUID());
        UUID requestId = UUID.randomUUID();

        assertEquals(expected, service.openSessionTurn(adventureId, owner, requestId));

        ArgumentCaptor<SubmitRuntimeTurnCommand> command = ArgumentCaptor.forClass(SubmitRuntimeTurnCommand.class);
        verify(service).submitTurn(command.capture());
        assertEquals(adventureId, command.getValue().adventureId());
        assertEquals(owner, command.getValue().ownerPlayerId());
        assertEquals(requestId, command.getValue().commandId());
        assertEquals("SESSION_OPENING", command.getValue().action());
        assertEquals(-1, command.getValue().expectedVersion());
        assertEquals(true, command.getValue().advancesState());
        assertEquals(true, command.getValue().gmOnly());
    }
}
