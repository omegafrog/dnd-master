package com.dndmaster.adventure.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dndmaster.adventure.domain.runtime.event.SessionEvent;
import com.dndmaster.adventure.application.runtime.SessionEventRepository;
import com.dndmaster.adventure.application.saved.AdventureRepository;
import com.dndmaster.adventure.domain.adventure.Adventure;
import com.dndmaster.adventure.domain.adventure.AdventureContext;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.CharacterSheetId;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.adventure.RuleSetId;
import com.dndmaster.adventure.domain.adventure.ScenarioId;
import com.dndmaster.adventure.domain.adventure.SessionId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class AdventureEventControllerTest {
    @Test
    void resolves_session_cursor_and_replays_only_owned_adventure() throws InterruptedException {
        UUID owner = UUID.randomUUID();
        Adventure adventure = adventure(owner);
        RecordingEvents events = new RecordingEvents();
        AdventureRepository adventures = repository(adventure);
        AuthenticatedPlayerResolver resolver = mock(AuthenticatedPlayerResolver.class);
        when(resolver.playerId()).thenReturn(owner);

        SseEmitter emitter = new AdventureEventController(events, resolver, adventures)
                .events(adventure.id().value(), 3, null);
        for (int attempt = 0; attempt < 20 && events.sessionId == null; attempt++) Thread.sleep(25);
        emitter.complete();

        assertEquals(adventure.sessionId().value(), events.sessionId);
        assertEquals(3, events.afterVersion);
    }

    @Test
    void rejects_foreign_adventure_before_opening_stream() {
        UUID owner = UUID.randomUUID();
        Adventure adventure = adventure(owner);
        AuthenticatedPlayerResolver resolver = mock(AuthenticatedPlayerResolver.class);
        when(resolver.playerId()).thenReturn(UUID.randomUUID());

        assertThrows(RuntimeException.class, () -> new AdventureEventController(
                new RecordingEvents(), resolver, repository(adventure)).events(adventure.id().value(), -1, null));
    }

    private static Adventure adventure(UUID owner) {
        return Adventure.create(AdventureId.generate(), SessionId.generate(), new OwnerPlayerId(owner),
                new ScenarioId(UUID.randomUUID()), new RuleSetId(UUID.randomUUID()), new CharacterSheetId(UUID.randomUUID()),
                new AdventureContext("scene", null, null, null));
    }

    private static AdventureRepository repository(Adventure adventure) {
        return new AdventureRepository() {
            @Override public Optional<Adventure> findById(AdventureId id) { return Optional.of(adventure); }
            @Override public List<Adventure> findSavedByOwner(OwnerPlayerId owner) { return List.of(); }
            @Override public void save(Adventure value) { }
        };
    }

    private static final class RecordingEvents implements SessionEventRepository {
        UUID sessionId; long afterVersion;
        @Override public void append(SessionEvent event) { }
        @Override public List<SessionEvent> after(UUID sessionId, long version) {
            this.sessionId = sessionId; this.afterVersion = version; return new ArrayList<>();
        }
    }
}
