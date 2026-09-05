package com.dndmaster.adventure.api;

import com.dndmaster.adventure.application.combat.CombatEncounterRepository;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.combat.PlayerCombatProjectionPolicy;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
public final class CombatController {
    private final CombatEncounterRepository repository;
    private final AuthenticatedPlayerResolver playerResolver;
    private final com.dndmaster.adventure.application.saved.AdventureRepository adventureRepository;
    public CombatController(CombatEncounterRepository repository, AuthenticatedPlayerResolver playerResolver,
                            com.dndmaster.adventure.application.saved.AdventureRepository adventureRepository) {
        this.repository = repository; this.playerResolver = playerResolver; this.adventureRepository = adventureRepository;
    }
    @GetMapping("/api/v1/adventures/{adventureId}/combat")
    public ResponseEntity<?> snapshot(@PathVariable UUID adventureId) {
        assertOwner(adventureId);
        return repository.findActive(adventureId)
                .map(e -> ResponseEntity.ok(PlayerCombatProjectionPolicy.toSnapshot(e, playerResolver.playerId())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
    @GetMapping(value = "/api/v1/adventures/{adventureId}/combat/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@PathVariable UUID adventureId) {
        assertOwner(adventureId);
        var emitter = new SseEmitter(15_000L);
        repository.findActive(adventureId).ifPresent(encounter -> {
            try {
                var snapshot = PlayerCombatProjectionPolicy.toSnapshot(encounter, playerResolver.playerId());
                emitter.send(SseEmitter.event().id(Long.toString(snapshot.eventCursor()))
                        .name("COMBAT_SNAPSHOT").data(snapshot));
            } catch (java.io.IOException exception) {
                emitter.completeWithError(exception);
            }
        });
        emitter.complete();
        return emitter;
    }
    private void assertOwner(UUID adventureId) {
        var adventure = adventureRepository.findById(new AdventureId(adventureId)).orElseThrow();
        if (!adventure.ownerPlayerId().value().equals(playerResolver.playerId())) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN);
    }
}
