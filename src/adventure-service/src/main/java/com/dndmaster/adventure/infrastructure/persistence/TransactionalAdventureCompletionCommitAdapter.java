package com.dndmaster.adventure.infrastructure.persistence;

import com.dndmaster.adventure.application.runtime.AdventureCompletionCommitPort;
import com.dndmaster.adventure.application.runtime.AdventureConversationCompactionCommitPort;
import com.dndmaster.adventure.application.runtime.ConversationCompactionJob;
import com.dndmaster.adventure.application.session.AdventureSessionRepository;
import com.dndmaster.adventure.application.saved.AdventureRepository;
import com.dndmaster.adventure.domain.adventure.Adventure;
import com.dndmaster.adventure.domain.adventure.AdventureSession;
import java.util.Objects;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Commits the adventure, compaction request, and linked session as one local transaction. */
public final class TransactionalAdventureCompletionCommitAdapter implements AdventureCompletionCommitPort {
    private final AdventureRepository adventures;
    private final AdventureSessionRepository sessions;
    private final TransactionTemplate transactions;

    public TransactionalAdventureCompletionCommitAdapter(AdventureRepository adventures,
            AdventureSessionRepository sessions, PlatformTransactionManager transactionManager) {
        this.adventures = Objects.requireNonNull(adventures);
        this.sessions = Objects.requireNonNull(sessions);
        this.transactions = new TransactionTemplate(Objects.requireNonNull(transactionManager));
    }

    @Override
    public void saveCompleted(Adventure adventure, ConversationCompactionJob compactionJob) {
        Objects.requireNonNull(adventure, "completed adventure is required");
        if (adventure.status() != com.dndmaster.adventure.domain.adventure.AdventureStatus.COMPLETED) {
            throw new IllegalArgumentException("adventure must have a committed conclusion");
        }
        transactions.executeWithoutResult(status -> {
            if (compactionJob == null) {
                adventures.save(adventure);
            } else if (adventures instanceof AdventureConversationCompactionCommitPort atomic) {
                atomic.saveConfirmedTurnAndRegister(adventure, compactionJob);
            } else {
                throw new IllegalStateException("confirmed adventure storage must atomically register conversation compaction work");
            }
            AdventureSession session = sessions.findById(adventure.sessionId()).orElseThrow(
                    () -> new IllegalStateException("completed adventure session was not found"));
            if (!adventure.id().equals(session.startedAdventureId())) {
                throw new IllegalStateException("completed adventure does not match its session");
            }
            if (session.status() == AdventureSession.Status.COMPLETED) return;
            if (session.status() != AdventureSession.Status.STARTED) {
                throw new IllegalStateException("only a started session can close with an adventure conclusion");
            }
            long expectedVersion = session.version();
            session.complete();
            sessions.save(session, expectedVersion);
        });
    }
}
