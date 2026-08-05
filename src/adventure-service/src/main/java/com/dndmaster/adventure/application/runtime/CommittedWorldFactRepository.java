package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.runtime.fact.CommittedWorldFact;
import com.dndmaster.adventure.domain.runtime.fact.CommittedWorldFactLedger;
import java.util.UUID;

public interface CommittedWorldFactRepository {
    CommittedWorldFactLedger findBySessionId(UUID sessionId);
    void append(UUID sessionId, CommittedWorldFact fact);
}
