package com.dndmaster.adventure.application.session;

import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.adventure.SessionId;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AdventureAiRequestApplicationService {
    private final AdventureSessionRepository repository;

    public AdventureAiRequestApplicationService(AdventureSessionRepository repository) {
        this.repository = Objects.requireNonNull(repository, "adventure session repository must not be null");
    }

    public Permit begin(SessionId sessionId, OwnerPlayerId ownerPlayerId, UUID requestId) {
        Objects.requireNonNull(sessionId, "session id must not be null");
        Objects.requireNonNull(ownerPlayerId, "owner player id must not be null");
        Objects.requireNonNull(requestId, "AI request id must not be null");
        if (!repository.tryAcquireAiRequest(sessionId, ownerPlayerId, requestId)) {
            throw new AdventureAiRequestInProgressException();
        }
        return new Permit(repository, sessionId, ownerPlayerId, requestId);
    }

    public boolean release(SessionId sessionId, OwnerPlayerId ownerPlayerId, UUID requestId) {
        Objects.requireNonNull(sessionId, "session id must not be null");
        Objects.requireNonNull(ownerPlayerId, "owner player id must not be null");
        Objects.requireNonNull(requestId, "AI request id must not be null");
        return repository.releaseAiRequest(sessionId, ownerPlayerId, requestId);
    }

    public static final class Permit implements AutoCloseable {
        private final AdventureSessionRepository repository;
        private final SessionId sessionId;
        private final OwnerPlayerId ownerPlayerId;
        private final UUID requestId;
        private final AtomicBoolean open = new AtomicBoolean(true);

        private Permit(AdventureSessionRepository repository, SessionId sessionId,
                OwnerPlayerId ownerPlayerId, UUID requestId) {
            this.repository = repository;
            this.sessionId = sessionId;
            this.ownerPlayerId = ownerPlayerId;
            this.requestId = requestId;
        }

        @Override
        public void close() {
            if (open.compareAndSet(true, false)
                    && !repository.releaseAiRequest(sessionId, ownerPlayerId, requestId)) {
                throw new IllegalStateException("matching AI request could not be released");
            }
        }

        /** A scheduled combat follow-up owns the same request until its terminal outcome. */
        public void handOffToCombatFollowUp() {
            open.compareAndSet(true, false);
        }
    }
}
