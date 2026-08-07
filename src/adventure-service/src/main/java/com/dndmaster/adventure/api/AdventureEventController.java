package com.dndmaster.adventure.api;

import com.dndmaster.adventure.application.runtime.SessionEventRepository;
import com.dndmaster.adventure.application.runtime.PlayerProjection;
import com.dndmaster.adventure.domain.runtime.event.SessionEvent;
import com.dndmaster.adventure.application.saved.AdventureRepository;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@RestController
public final class AdventureEventController {
    private final SessionEventRepository events;
    private final AuthenticatedPlayerResolver playerResolver;
    private final AdventureRepository adventures;

    public AdventureEventController(SessionEventRepository events, AuthenticatedPlayerResolver playerResolver, AdventureRepository adventures) {
        this.events = events; this.playerResolver = playerResolver; this.adventures = adventures;
    }

    @GetMapping(value = "/api/v1/adventures/{adventureId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter events(@PathVariable UUID adventureId, @RequestParam(defaultValue = "-1") long afterVersion,
                      @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
        UUID owner = playerResolver.playerId();
        var adventure = adventures.findById(new AdventureId(adventureId)).orElseThrow();
        adventure.reopen(new OwnerPlayerId(owner));
        long cursor = afterVersion;
        if (lastEventId != null && !lastEventId.isBlank()) {
            try { cursor = Math.max(cursor, Long.parseLong(lastEventId)); } catch (NumberFormatException ignored) { }
        }
        SseEmitter emitter = new SseEmitter(30_000L);
        UUID sessionId = adventure.sessionId().value();
        var executor = Executors.newSingleThreadScheduledExecutor();
        final long[] next = {cursor};
        ScheduledFuture<?> poll = executor.scheduleAtFixedRate(() -> {
            try {
                for (SessionEvent event : events.after(sessionId, next[0])) {
                    String payload = playerPayload(event);
                    emitter.send(SseEmitter.event().id(Long.toString(event.version())).name(event.type()).data(payload));
                    next[0] = Math.max(next[0], event.version());
                }
            } catch (IOException exception) { emitter.completeWithError(exception); }
        }, 0, 500, TimeUnit.MILLISECONDS);
        emitter.onCompletion(() -> { poll.cancel(true); executor.shutdownNow(); });
        emitter.onTimeout(() -> { poll.cancel(true); executor.shutdownNow(); emitter.complete(); });
        return emitter;
    }

    static String playerPayload(SessionEvent event) {
        return "GM_TURN_COMMITTED".equals(event.type()) ? "turn committed" : "event updated";
    }
}
