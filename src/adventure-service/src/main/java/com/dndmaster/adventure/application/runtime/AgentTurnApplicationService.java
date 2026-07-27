package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.adventure.Adventure;
import com.dndmaster.adventure.domain.adventure.AdventurePartyMember;
import com.dndmaster.adventure.domain.adventure.CharacterSheetId;
import com.dndmaster.adventure.domain.adventure.ControlMode;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import java.util.Objects;

/** Runs one AGENT turn, then delegates application to the existing runtime command saga. */
public final class AgentTurnApplicationService {
    private final CharacterSheetReadPort characterSheetReadPort;
    private final AgentActionCandidatePort candidatePort;
    private final RuntimeTurnApplicationService runtimeTurnService;

    public AgentTurnApplicationService(CharacterSheetReadPort characterSheetReadPort, AgentActionCandidatePort candidatePort) {
        this(characterSheetReadPort, candidatePort, null);
    }

    public AgentTurnApplicationService(
            CharacterSheetReadPort characterSheetReadPort,
            AgentActionCandidatePort candidatePort,
            RuntimeTurnApplicationService runtimeTurnService) {
        this.characterSheetReadPort = Objects.requireNonNull(characterSheetReadPort);
        this.candidatePort = Objects.requireNonNull(candidatePort);
        this.runtimeTurnService = runtimeTurnService;
    }

    public PreparedAgentTurn prepareAgentTurn(Adventure adventure, OwnerPlayerId ownerPlayerId, TurnCursor cursor) {
        Objects.requireNonNull(adventure);
        Objects.requireNonNull(ownerPlayerId);
        Objects.requireNonNull(cursor);
        if (cursor.current().controlMode() != ControlMode.AGENT) {
            throw new IllegalStateException("current turn is waiting for direct player input");
        }
        CharacterSheetId sheetId = cursor.current().characterSheetId();
        CharacterSheetReadPort.CharacterSheet sheet = characterSheetReadPort.read(sheetId);
        AgentActionCandidate candidate = candidatePort.propose(new AgentActionCandidatePort.Request(
                adventure.id(), ownerPlayerId, sheetId, sheet, adventure.currentContext()));
        if (!sheetId.equals(candidate.characterSheetId())) {
            throw new IllegalStateException("agent candidate character does not own current turn");
        }
        return new PreparedAgentTurn(candidate, cursor);
    }

    public AgentTurnResult runAgentTurn(Adventure adventure, OwnerPlayerId ownerPlayerId, TurnCursor cursor) {
        if (runtimeTurnService == null) throw new IllegalStateException("runtime turn service is required to run an agent turn");
        PreparedAgentTurn prepared = prepareAgentTurn(adventure, ownerPlayerId, cursor);
        RuntimeTurnResult result = runtimeTurnService.submitTurn(new SubmitRuntimeTurnCommand(
                adventure.id(), ownerPlayerId, prepared.candidate().turnId(), prepared.candidate().commandId(), prepared.candidate().action()));
        return new AgentTurnResult(result, cursor.advanceAfterAgentTurn(prepared.candidate().characterSheetId()));
    }

    public record PreparedAgentTurn(AgentActionCandidate candidate, TurnCursor cursor) {}
    public record AgentTurnResult(RuntimeTurnResult result, TurnCursor nextCursor) {}
}
