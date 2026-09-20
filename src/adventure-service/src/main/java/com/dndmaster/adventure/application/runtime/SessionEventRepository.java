package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.runtime.event.SessionEvent;

import java.util.List;
import java.util.UUID;

public interface SessionEventRepository {
    void append(SessionEvent event);
    default SessionEvent appendNext(UUID sessionId, UUID eventId, String type, String payload) {
        throw new UnsupportedOperationException("atomic session event append is not supported");
    }
    List<SessionEvent> after(UUID sessionId, long version);
}
