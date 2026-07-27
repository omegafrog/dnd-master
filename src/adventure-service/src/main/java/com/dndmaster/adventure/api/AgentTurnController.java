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

    public AgentTurnController(AdventureRepository adventureRepository, AgentTurnApplicationService agentTurnService) {
        this.adventureRepository = adventureRepository;
        this.agentTurnService = agentTurnService;
    }

    @PostMapping("/{adventureId}/agent-turns")
    AgentTurnResponse run(@PathVariable UUID adventureId, @RequestBody AgentTurnRequest request) {
        var adventure = adventureRepository.findById(new AdventureId(adventureId))
                .orElseThrow(() -> new IllegalStateException("adventure not found"));
        var result = agentTurnService.runAgentTurn(
                adventure, new OwnerPlayerId(request.playerId()), new TurnCursor(adventure.party(), request.turnIndex()));
        return AgentTurnResponse.from(result.result());
    }

    public record AgentTurnRequest(UUID playerId, int turnIndex) {}

    public record AgentTurnResponse(UUID turnId, UUID adventureId, String narration, String judgment,
                                    String currentScene, List<String> sourceRefs, List<String> warnings, long version) {
        static AgentTurnResponse from(RuntimeTurnResult result) {
            return new AgentTurnResponse(result.turn().turnId(), result.turn().adventureId().value(),
                    result.turn().plan().narration(), result.turn().plan().judgment(), result.context().currentScene(),
                    result.turn().citations(), result.turn().warnings(), result.version());
        }
    }
}
