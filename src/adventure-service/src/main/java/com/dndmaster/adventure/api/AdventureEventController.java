package com.dndmaster.adventure.api;

import com.dndmaster.adventure.application.runtime.SessionEvent;
import com.dndmaster.adventure.application.runtime.SessionEventRepository;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
public final class AdventureEventController {
    private final SessionEventRepository events;
    private final AuthenticatedPlayerResolver playerResolver;

    public AdventureEventController(SessionEventRepository events, AuthenticatedPlayerResolver playerResolver) {
        this.events = events; this.playerResolver = playerResolver;
    }

    @GetMapping(value = "/api/v1/adventures/{adventureId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter events(@PathVariable UUID adventureId, @RequestParam(defaultValue = "-1") long afterVersion,
                      @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
        playerResolver.playerId();
        long cursor = afterVersion;
        if (lastEventId != null && !lastEventId.isBlank()) {
            try { cursor = Math.max(cursor, Long.parseLong(lastEventId)); } catch (NumberFormatException ignored) { }
        }
        SseEmitter emitter = new SseEmitter(30_000L);
        try {
            for (SessionEvent event : events.after(adventureId, cursor)) {
                emitter.send(SseEmitter.event().id(Long.toString(event.version())).name(event.type()).data(event.payload()));
            }
            emitter.complete();
        } catch (IOException exception) { emitter.completeWithError(exception); }
        return emitter;
    }
}
