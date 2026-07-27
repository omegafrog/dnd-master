package com.dndmaster.adventure.api;

import com.dndmaster.adventure.application.runtime.AgentTurnApplicationService;
import com.dndmaster.adventure.application.runtime.RuntimeTurnResult;
import com.dndmaster.adventure.application.runtime.TurnCursor;
import com.dndmaster.adventure.application.saved.AdventureRepository;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/adventures")
public final class AgentTurnController {
    private final AdventureRepository adventureRepository;
    private final AgentTurnApplicationService agentTurnService;
    private final AuthenticatedPlayerResolver playerResolver;

    public AgentTurnController(AdventureRepository adventureRepository, AgentTurnApplicationService agentTurnService, AuthenticatedPlayerResolver playerResolver) {
        this.adventureRepository = adventureRepository;
        this.agentTurnService = agentTurnService;
        this.playerResolver = playerResolver;
    }

    @PostMapping("/{adventureId}/agent-turns")
    AgentTurnResponse run(@PathVariable UUID adventureId, @RequestBody AgentTurnRequest request) {
        var adventure = adventureRepository.findById(new AdventureId(adventureId))
                .orElseThrow(() -> new IllegalStateException("adventure not found"));
        UUID authenticatedPlayer = playerResolver.playerId();
        if (!authenticatedPlayer.equals(request.playerId())) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "player mismatch");
        var result = agentTurnService.runAgentTurn(
                adventure, new OwnerPlayerId(authenticatedPlayer), new TurnCursor(adventure.party(), adventure.turnIndex()), request.expectedVersion());
        return AgentTurnResponse.from(result);
    }

    public record AgentTurnRequest(UUID playerId, long expectedVersion) {}

    public record AgentTurnResponse(UUID turnId, UUID adventureId, String narration, String judgment,
                                    String currentScene, List<String> sourceRefs, List<String> warnings, long version,
                                    int nextTurnIndex, String nextControlMode) {
        static AgentTurnResponse from(AgentTurnApplicationService.AgentTurnResult result) {
            RuntimeTurnResult runtime = result.result();
            return new AgentTurnResponse(runtime.turn().turnId(), runtime.turn().adventureId().value(),
                    runtime.turn().plan().narration(), runtime.turn().plan().judgment(), runtime.context().currentScene(),
                    runtime.turn().citations(), runtime.turn().warnings(), runtime.version(), result.nextCursor().index(),
                    result.nextCursor().current().controlMode().name());
        }
    }
}
