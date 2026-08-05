package com.dndmaster.adventure.application.runtime;

import java.util.List;
import java.util.UUID;

public interface SessionEventRepository {
    void append(SessionEvent event);
    List<SessionEvent> after(UUID sessionId, long version);
}
