package com.dndmaster.adventure.application.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.adventure.SessionId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdventureAiRequestApplicationServiceTest {
    @Test
    void rejects_a_competing_request_without_starting_work() {
        AdventureSessionRepository repository = mock(AdventureSessionRepository.class);
        AdventureAiRequestApplicationService service = new AdventureAiRequestApplicationService(repository);
        SessionId sessionId = SessionId.generate();
        OwnerPlayerId owner = new OwnerPlayerId(UUID.randomUUID());
        UUID requestId = UUID.randomUUID();
        when(repository.tryAcquireAiRequest(sessionId, owner, requestId)).thenReturn(false);

        assertThrows(AdventureAiRequestInProgressException.class,
                () -> service.begin(sessionId, owner, requestId));

        verify(repository, never()).releaseAiRequest(sessionId, owner, requestId);
    }

    @Test
    void releases_the_same_request_only_after_successful_work_finishes() {
        AdventureSessionRepository repository = mock(AdventureSessionRepository.class);
        AdventureAiRequestApplicationService service = new AdventureAiRequestApplicationService(repository);
        SessionId sessionId = SessionId.generate();
        OwnerPlayerId owner = new OwnerPlayerId(UUID.randomUUID());
        UUID requestId = UUID.randomUUID();
        boolean[] worldStateProcessed = {false};
        when(repository.tryAcquireAiRequest(sessionId, owner, requestId)).thenReturn(true);
        when(repository.releaseAiRequest(sessionId, owner, requestId)).thenAnswer(ignored -> {
            assertTrue(worldStateProcessed[0]);
            return true;
        });

        try (AdventureAiRequestApplicationService.Permit ignored = service.begin(sessionId, owner, requestId)) {
            worldStateProcessed[0] = true;
        }

        verify(repository).releaseAiRequest(sessionId, owner, requestId);
    }

    @Test
    void releases_the_same_request_when_processing_fails_before_world_state_changes() {
        AdventureSessionRepository repository = mock(AdventureSessionRepository.class);
        AdventureAiRequestApplicationService service = new AdventureAiRequestApplicationService(repository);
        SessionId sessionId = SessionId.generate();
        OwnerPlayerId owner = new OwnerPlayerId(UUID.randomUUID());
        UUID requestId = UUID.randomUUID();
        when(repository.tryAcquireAiRequest(sessionId, owner, requestId)).thenReturn(true);
        when(repository.releaseAiRequest(sessionId, owner, requestId)).thenReturn(true);

        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> {
            try (AdventureAiRequestApplicationService.Permit ignored = service.begin(sessionId, owner, requestId)) {
                throw new IllegalStateException("AI execution failed");
            }
        });

        assertEquals("AI execution failed", failure.getMessage());
        verify(repository).releaseAiRequest(sessionId, owner, requestId);
    }
}
