package com.dndmaster.adventure.api;

import com.dndmaster.adventure.application.combat.CombatEndResult;
import com.dndmaster.adventure.application.combat.CombatLifecycleApplicationService;
import com.dndmaster.adventure.application.runtime.GmTurnRepository;
import com.dndmaster.adventure.domain.combat.CombatEndProposal;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Internal AI-GM boundary for the only command allowed to end combat. */
@RestController
public final class CombatEndController {
    private final CombatLifecycleApplicationService lifecycle;
    private final GmTurnRepository gmTurns;
    private final ApiRequestGuard requestGuard;

    public CombatEndController(CombatLifecycleApplicationService lifecycle, GmTurnRepository gmTurns,
            @Value("${adventure.integration.internal-token:${INTERNAL_SERVICE_TOKEN:}}") String internalToken) {
        this.lifecycle = lifecycle;
        this.gmTurns = gmTurns;
        this.requestGuard = new ApiRequestGuard(internalToken);
    }

    @PostMapping({"/internal/v1/adventures/{adventureId}/combat/end",
            "/api/v1/internal/adventures/{adventureId}/combat/end"})
    public ResponseEntity<CombatEndResponse> end(
            @PathVariable UUID adventureId,
            @RequestHeader(value = "X-Internal-Token", required = false) String internalToken,
            @RequestHeader("X-GM-Turn-ID") UUID gmTurnId,
            @RequestBody CombatEndProposal proposal) {
        requestGuard.internal(internalToken);
        var gmTurn = gmTurns.findByTurnIdAndAdventureId(gmTurnId, adventureId)
                .orElseThrow(() -> new ApiRequestGuard.ApiContractException(404, "GM_TURN_NOT_FOUND"));
        CombatEndResult result = lifecycle.endFromCommittedGmTurn(adventureId, gmTurn, proposal);
        return ResponseEntity.ok(CombatEndResponse.from(result));
    }

    public record CombatEndResponse(UUID adventureId, UUID encounterId, long encounterVersion,
                                    String reason, String summary, boolean detailedReplayAvailable) {
        static CombatEndResponse from(CombatEndResult result) {
            var summary = result.summary();
            return new CombatEndResponse(summary.adventureId(), summary.encounterId(), result.encounterVersion(),
                    summary.reason(), summary.summary(), summary.detailedReplayAvailable());
        }
    }
}
