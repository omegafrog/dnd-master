package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.runtime.clock.AdventureClock;
import java.util.Optional;
import java.util.UUID;

public interface AdventureClockRepository {
    Optional<AdventureClock> findBySessionId(UUID sessionId);
    void save(AdventureClock clock, long expectedVersion);
}
