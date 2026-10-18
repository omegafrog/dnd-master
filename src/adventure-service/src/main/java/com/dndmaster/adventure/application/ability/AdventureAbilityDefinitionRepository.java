package com.dndmaster.adventure.application.ability;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Optional;
import java.util.UUID;

public interface AdventureAbilityDefinitionRepository {
    Optional<StoredAbilityDefinition> find(UUID adventureId, String abilityId);
    StoredAbilityDefinition prepare(UUID adventureId, String abilityId, JsonNode definition);
    void activate(UUID adventureId, String abilityId);

    record StoredAbilityDefinition(String status, JsonNode definition, long revision) {}
}
