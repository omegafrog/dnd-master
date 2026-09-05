package com.dndmaster.adventure.api;

import com.dndmaster.adventure.application.combat.CombatEncounterRepository;
import com.dndmaster.adventure.application.combat.CombatActionApplicationService;
import com.dndmaster.adventure.application.combat.CombatActionCommand;
import com.dndmaster.adventure.application.combat.CombatActionResponse;
import com.dndmaster.adventure.application.combat.CombatActorRole;
import com.dndmaster.adventure.application.combat.CombatReactionApplicationService;
import com.dndmaster.adventure.application.combat.ResolveReactionCommand;
import com.dndmaster.adventure.application.combat.FreeFormCombatCommand;
import com.dndmaster.adventure.domain.combat.ReactionChoice;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.CharacterSheetId;
import com.dndmaster.adventure.domain.combat.PlayerCombatProjectionPolicy;
import com.dndmaster.adventure.domain.combat.NarrativeCombatPosition;
import com.dndmaster.adventure.domain.combat.FreeFormInterpretationPolicy;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
public final class CombatController {
    private final CombatEncounterRepository repository;
    private final AuthenticatedPlayerResolver playerResolver;
    private final com.dndmaster.adventure.application.saved.AdventureRepository adventureRepository;
    private final com.dndmaster.adventure.application.combat.CombatEventRepository eventRepository;
    private final CombatActionApplicationService actionService;
    private final CombatReactionApplicationService reactionService;
    public CombatController(CombatEncounterRepository repository, AuthenticatedPlayerResolver playerResolver,
                            com.dndmaster.adventure.application.saved.AdventureRepository adventureRepository,
                            com.dndmaster.adventure.application.combat.CombatEventRepository eventRepository,
                            CombatActionApplicationService actionService,
                            CombatReactionApplicationService reactionService) {
        this.repository = repository; this.playerResolver = playerResolver; this.adventureRepository = adventureRepository; this.eventRepository = eventRepository;
        this.actionService = actionService; this.reactionService = reactionService;
    }

    @PostMapping("/api/v1/adventures/{adventureId}/combat/reactions/{reactionId}")
    public ResponseEntity<com.dndmaster.adventure.application.combat.ReactionResolutionResponse> resolveReaction(
            @PathVariable UUID adventureId, @PathVariable UUID reactionId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("If-Match-Version") long expectedVersion,
            @RequestBody ReactionRequest request) {
        assertOwner(adventureId);
        var encounter = repository.findActive(adventureId).orElseThrow();
        var pending = encounter.pendingReaction();
        if (pending == null || !pending.reactionId().equals(reactionId)) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "REACTION_NOT_PENDING");
        }
        final ReactionChoice choice;
        try { choice = ReactionChoice.valueOf(request.choice()); }
        catch (RuntimeException exception) { throw new ApiRequestGuard.ApiContractException(400, "INVALID_REACTION_CHOICE"); }
        return ResponseEntity.accepted().body(reactionService.resolve(new ResolveReactionCommand(
                adventureId, reactionId, request.actorId() == null ? pending.eligibleActorId() : request.actorId(),
                choice, expectedVersion)));
    }

    @PostMapping("/api/v1/adventures/{adventureId}/combat/actions")
    public ResponseEntity<CombatActionResponse> action(@PathVariable UUID adventureId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("If-Match-Version") long expectedVersion,
            @RequestBody CombatActionRequest request) {
        var adventure = assertOwnerAndLoad(adventureId);
        UUID commandId = uuidHeader(idempotencyKey, "Idempotency-Key");
        return ResponseEntity.accepted().body(actionService.submit(new CombatActionCommand(commandId,
                adventure.id(), adventure.sessionId().value(), adventure.ruleSetId(),
                new CharacterSheetId(request.characterSheetId()), request.combatMapId(), CombatActorRole.PLAYER,
                request.action(), path(request.movementPath()), playerResolver.playerId(), request.tokenId() == null
                        ? request.characterSheetId() : request.tokenId(), expectedVersion,
                request.targetArmorClass(), request.attackModifier(), request.targetCharacterSheetId() == null
                        ? null : new CharacterSheetId(request.targetCharacterSheetId()), request.damageAmount(), false,
                request.narrativePosition() == null ? null : request.narrativePosition().toDomain(), request.movementDistance(), request.mapVersion())));
    }

    @PostMapping("/api/v1/adventures/{adventureId}/combat/free-form")
    public ResponseEntity<CombatActionResponse> freeForm(@PathVariable UUID adventureId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("If-Match-Version") long expectedVersion,
            @RequestBody FreeFormActionRequest request) {
        var adventure = assertOwnerAndLoad(adventureId);
        UUID commandId = uuidHeader(idempotencyKey, "Idempotency-Key");
        var actor = new CharacterSheetId(request.characterSheetId());
        var base = new CombatActionCommand(commandId, adventure.id(), adventure.sessionId().value(), adventure.ruleSetId(),
                actor, null, CombatActorRole.PLAYER, "FREE_FORM", null, playerResolver.playerId(), actor.value(),
                expectedVersion, null, null, null, null, false);
        return ResponseEntity.accepted().body(actionService.submitFreeForm(new FreeFormCombatCommand(base,
                FreeFormInterpretationPolicy.accept(actor.value(), request.declaration()))));
    }

    @PostMapping("/api/v1/adventures/{adventureId}/combat/turn/end")
    public ResponseEntity<CombatActionResponse> endTurn(@PathVariable UUID adventureId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("If-Match-Version") long expectedVersion,
            @RequestBody TurnEndRequest request) {
        var adventure = assertOwnerAndLoad(adventureId);
        UUID commandId = uuidHeader(idempotencyKey, "Idempotency-Key");
        return ResponseEntity.accepted().body(actionService.endTurn(new CombatActionCommand(commandId,
                adventure.id(), adventure.sessionId().value(), adventure.ruleSetId(),
                new CharacterSheetId(request.characterSheetId()), null, CombatActorRole.PLAYER,
                "END_TURN", null, playerResolver.playerId(), request.characterSheetId(), expectedVersion,
                null, null, null, null, false)));
    }
    @GetMapping("/api/v1/adventures/{adventureId}/combat")
    public ResponseEntity<?> snapshot(@PathVariable UUID adventureId) {
        assertOwner(adventureId);
        return repository.findActive(adventureId)
                .map(e -> ResponseEntity.ok(PlayerCombatProjectionPolicy.toSnapshot(e, playerResolver.playerId())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
    @GetMapping(value = "/api/v1/adventures/{adventureId}/combat/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@PathVariable UUID adventureId,
                             @RequestParam(defaultValue = "-1") long afterSequence,
                             @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
        assertOwner(adventureId);
        long cursor = afterSequence;
        if (lastEventId != null && !lastEventId.isBlank()) try { cursor = Math.max(cursor, Long.parseLong(lastEventId)); } catch (NumberFormatException ignored) { }
        var emitter = new SseEmitter(30_000L);
        final long initialCursor = cursor;
        repository.findActive(adventureId).ifPresent(encounter -> {
            var executor = Executors.newSingleThreadScheduledExecutor();
            final long[] next = {initialCursor};
            Runnable replay = () -> { try {
                for (var event : eventRepository.after(encounter.encounterId(), next[0])) {
                    emitter.send(SseEmitter.event().id(Long.toString(event.sequence())).name(event.eventType()).data(event.playerPayload()));
                    next[0] = event.sequence();
                }
            } catch (java.io.IOException exception) { emitter.completeWithError(exception); } };
            executor.scheduleAtFixedRate(replay, 0, 500, TimeUnit.MILLISECONDS);
            emitter.onCompletion(executor::shutdownNow);
            emitter.onTimeout(() -> { executor.shutdownNow(); emitter.complete(); });
        });
        return emitter;
    }
    private void assertOwner(UUID adventureId) {
        assertOwnerAndLoad(adventureId);
    }

    private com.dndmaster.adventure.domain.adventure.Adventure assertOwnerAndLoad(UUID adventureId) {
        var adventure = adventureRepository.findById(new AdventureId(adventureId)).orElseThrow();
        if (!adventure.ownerPlayerId().value().equals(playerResolver.playerId())) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN);
        return adventure;
    }

    private static UUID uuidHeader(String value, String name) {
        try { return UUID.fromString(value); }
        catch (RuntimeException exception) { throw new ApiRequestGuard.ApiContractException(400, "INVALID_" + name.toUpperCase().replace('-', '_')); }
    }

    public record CombatActionRequest(UUID characterSheetId, String action, Integer targetArmorClass,
                                      Integer attackModifier, UUID targetCharacterSheetId, Integer damageAmount,
                                      UUID combatMapId, UUID tokenId, List<PositionRequest> movementPath,
                                      NarrativePositionRequest narrativePosition, Integer movementDistance, Long mapVersion) {
        public CombatActionRequest(UUID characterSheetId, String action, Integer targetArmorClass,
                                    Integer attackModifier, UUID targetCharacterSheetId, Integer damageAmount) {
            this(characterSheetId, action, targetArmorClass, attackModifier, targetCharacterSheetId, damageAmount,
                    null, null, null, null, null, null);
        }
    }

    public record PositionRequest(int x, int y) {}
    public record NarrativePositionRequest(UUID subjectId, UUID targetId, String rangeBand, String cover) {
        NarrativeCombatPosition toDomain() {
            return new NarrativeCombatPosition(subjectId, targetId, rangeBand, cover);
        }
    }

    private static String path(List<PositionRequest> positions) {
        if (positions == null || positions.isEmpty()) return null;
        return positions.stream().map(position -> position.x() + "," + position.y())
                .reduce((left, right) -> left + ";" + right).orElse(null);
    }

    public record TurnEndRequest(UUID characterSheetId) {}
    public record FreeFormActionRequest(UUID characterSheetId, String declaration) {}
    public record ReactionRequest(String choice, UUID actorId) {
        public ReactionRequest(String choice) { this(choice, null); }
    }
}
