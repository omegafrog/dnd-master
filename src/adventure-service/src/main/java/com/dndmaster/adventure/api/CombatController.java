package com.dndmaster.adventure.api;

import com.dndmaster.adventure.application.combat.CombatEncounterRepository;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.combat.PlayerCombatProjectionPolicy;
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
    public CombatController(CombatEncounterRepository repository, AuthenticatedPlayerResolver playerResolver,
                            com.dndmaster.adventure.application.saved.AdventureRepository adventureRepository,
                            com.dndmaster.adventure.application.combat.CombatEventRepository eventRepository) {
        this.repository = repository; this.playerResolver = playerResolver; this.adventureRepository = adventureRepository; this.eventRepository = eventRepository;
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
        var adventure = adventureRepository.findById(new AdventureId(adventureId)).orElseThrow();
        if (!adventure.ownerPlayerId().value().equals(playerResolver.playerId())) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN);
    }
}
