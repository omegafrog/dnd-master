package com.dndmaster.adventure.application.runtime;

import java.util.Optional;
import java.util.UUID;

@FunctionalInterface
public interface StoryContinuityContextProvider {
    Optional<StoryContinuityContext> load(UUID sessionId);
}
