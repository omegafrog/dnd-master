package com.dndmaster.adventure.application.runtime;

import java.util.Optional;
import java.util.UUID;

public interface GameSystemDefinitionPort {
    Optional<Definition> find(UUID sessionId);
    record Definition(long version, String definitionJson) {}
}
