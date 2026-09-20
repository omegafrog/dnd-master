package com.dndmaster.diceroll.api;

import com.dndmaster.diceroll.application.DiceRollApplicationService;
import com.dndmaster.diceroll.application.RollCommand;
import com.dndmaster.diceroll.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping
public class DiceRollController {
    private final DiceRollApplicationService diceRollService;
    private final ApiRequestGuard requestGuard;

    public DiceRollController(DiceRollApplicationService diceRollService, ApiRequestGuard requestGuard) {
        this.diceRollService = diceRollService; this.requestGuard = requestGuard;
    }

    @PostMapping("/internal/v1/dice-rolls/player")
    DiceRollResponse playerRoll(@RequestHeader("X-Internal-Token") String token,
            @RequestHeader("Idempotency-Key") String idempotencyKey, @RequestBody DiceRollRequest request) {
        requestGuard.internal(token);
        requestGuard.idempotencyKey(idempotencyKey, request.commandId());
        RollCommand command = toCommand(request);
        return DiceRollResponse.from(diceRollService.executePlayerRoll(command));
    }

    @PostMapping("/internal/v1/dice-rolls/ai")
    DiceRollResponse aiRoll(@RequestHeader("X-Internal-Token") String token, @RequestBody DiceRollRequest request) {
        requestGuard.internal(token);
        RollCommand command = toCommand(request);
        return DiceRollResponse.from(diceRollService.executeAiRoll(command));
    }

    @PostMapping("/internal/v1/dice-rolls/enemy-observation")
    DiceRollResponse enemyObservationRoll(@RequestHeader("X-Internal-Token") String token,
            @RequestHeader("Idempotency-Key") String idempotencyKey, @RequestBody DiceRollRequest request) {
        requestGuard.internal(token);
        requestGuard.idempotencyKey(idempotencyKey, request.commandId());
        if (!"ENEMY".equals(request.scope())) throw new ApiRequestGuard.ApiContractException(400, "ENEMY_SCOPE_REQUIRED");
        if (request.ruleReference() == null || request.ruleReference().isBlank()) {
            throw new ApiRequestGuard.ApiContractException(400, "RULE_REFERENCE_REQUIRED");
        }
        if (request.difficulty() == null || request.difficulty() < 0) {
            throw new ApiRequestGuard.ApiContractException(400, "DIFFICULTY_REQUIRED");
        }
        return DiceRollResponse.from(diceRollService.executeAiRoll(toCommand(request)));
    }

    @GetMapping("/internal/v1/dice-rolls/commands/{commandId}")
    DiceRollResponse findByCommand(@RequestHeader("X-Internal-Token") String token, @PathVariable UUID commandId) {
        requestGuard.internal(token);
        return DiceRollResponse.from(diceRollService.findByCommandId(commandId).orElseThrow(() -> new IllegalStateException("dice command not found")));
    }

    private static RollCommand toCommand(DiceRollRequest request) {
        return new RollCommand(
                new AdventureId(request.adventureId()),
                new RuleSetId(request.ruleSetId()),
                RollScope.valueOf(request.scope()),
                new DiceExpression(request.count(), request.sides(), request.modifier()),
                request.sessionId(),
                request.turnId(),
                request.commandId(),
                request.expectedVersion(), request.ruleReference(), request.difficulty());
    }

    public record DiceRollRequest(
            UUID adventureId, UUID ruleSetId, String scope,
            int count, int sides, int modifier,
            UUID sessionId, UUID turnId, UUID commandId, long expectedVersion,
            String ruleReference, Integer difficulty) {}
}
