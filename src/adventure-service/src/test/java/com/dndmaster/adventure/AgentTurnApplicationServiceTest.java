package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.adventure.application.runtime.AgentActionCandidate;
import com.dndmaster.adventure.application.runtime.AgentActionCandidatePort;
import com.dndmaster.adventure.application.runtime.AgentTurnApplicationService;
import com.dndmaster.adventure.application.runtime.CharacterSheetReadPort;
import com.dndmaster.adventure.application.runtime.CharacterSheetReadPort.CharacterSheet;
import com.dndmaster.adventure.application.runtime.RuntimeTurnApplicationService;
import com.dndmaster.adventure.application.runtime.TurnCursor;
import com.dndmaster.adventure.domain.adventure.Adventure;
import com.dndmaster.adventure.domain.adventure.AdventureContext;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.AdventurePartyMember;
import com.dndmaster.adventure.domain.adventure.CharacterSheetId;
import com.dndmaster.adventure.domain.adventure.ControlMode;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.adventure.RuleSetId;
import com.dndmaster.adventure.domain.adventure.ScenarioId;
import com.dndmaster.adventure.domain.adventure.SessionId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AgentTurnApplicationServiceTest {
    @Test
    void waits_for_direct_input_and_reads_only_current_agent_sheet() {
        OwnerPlayerId owner = new OwnerPlayerId(UUID.randomUUID());
        CharacterSheetId direct = new CharacterSheetId(UUID.randomUUID());
        CharacterSheetId agent = new CharacterSheetId(UUID.randomUUID());
        Adventure adventure = adventure(owner, direct, agent);
        RecordingSheetReader sheets = new RecordingSheetReader();
        RecordingCandidatePlanner planner = new RecordingCandidatePlanner();
        TurnCursor directCursor = TurnCursor.start(adventure.party());
        assertEquals(ControlMode.DIRECT, directCursor.current().controlMode());
        assertThrows(IllegalStateException.class, () -> new AgentTurnApplicationService(sheets, planner)
                .prepareAgentTurn(adventure, owner, directCursor));
        assertEquals(List.of(), sheets.reads);

        TurnCursor agentCursor = directCursor.advanceAfterDirectInput(direct);
        assertEquals(ControlMode.AGENT, agentCursor.current().controlMode());
        assertEquals(agent, new AgentTurnApplicationService(sheets, planner)
                .prepareAgentTurn(adventure, owner, agentCursor).candidate().characterSheetId());
        assertEquals(List.of(agent), sheets.reads);
        assertEquals(List.of(agent), planner.characterSheetIds);
    }

    private static Adventure adventure(OwnerPlayerId owner, CharacterSheetId direct, CharacterSheetId agent) {
        return Adventure.create(
                AdventureId.generate(), SessionId.generate(), owner, new ScenarioId(UUID.randomUUID()),
                new RuleSetId(UUID.randomUUID()), List.of(
                        new AdventurePartyMember(direct, ControlMode.DIRECT, true, true, true, true, true, true),
                        new AdventurePartyMember(agent, ControlMode.AGENT, true, true, true, true, true, true)),
                new AdventureContext("start", null, null, null));
    }

    private static final class RecordingSheetReader implements CharacterSheetReadPort {
        private final java.util.ArrayList<CharacterSheetId> reads = new java.util.ArrayList<>();

        @Override
        public CharacterSheet read(CharacterSheetId id) {
            reads.add(id);
            return new CharacterSheet(id, "Agent", 3);
        }
    }

    private static final class RecordingCandidatePlanner implements AgentActionCandidatePort {
        private final java.util.ArrayList<CharacterSheetId> characterSheetIds = new java.util.ArrayList<>();

        @Override
        public AgentActionCandidate propose(Request request) {
            characterSheetIds.add(request.characterSheetId());
            return new AgentActionCandidate(UUID.randomUUID(), UUID.randomUUID(), request.characterSheetId(), "Take the next safe action");
        }
    }
}
