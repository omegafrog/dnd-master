package com.dndmaster.adventure.application.session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

public final class CharacterSheetDeletionWorker {
    private static final Logger log = LoggerFactory.getLogger(CharacterSheetDeletionWorker.class);
    private final AdventureSessionStartOutboxRepository outbox;
    private final CharacterSheetDeletionPort deletionPort;
    public CharacterSheetDeletionWorker(AdventureSessionStartOutboxRepository outbox, CharacterSheetDeletionPort deletionPort) { this.outbox = outbox; this.deletionPort = deletionPort; }
    @Scheduled(fixedDelayString = "${adventure.character-deletion.poll-delay-ms:1000}")
    public void process() { outbox.claimNextDeletion().ifPresent(event -> { try { deletionPort.delete(event.sessionId(), event.characterSheetIds()); outbox.completeDeletion(event.eventId()); log.info("character sheet deletion completed eventId={} attempts={}", event.eventId(), event.attempts()); } catch (RuntimeException e) { outbox.failDeletion(event.eventId(), e.getMessage()); log.warn("character sheet deletion failed eventId={} attempts={} reason={}, retrying", event.eventId(), event.attempts(), e.getMessage(), e); } }); }
}
