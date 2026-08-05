package com.dndmaster.ruleknowledge.domain.definition;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable, normalized rules contract derived from exactly one rulebook revision. */
public record GameSystemDefinitionRevision(UUID definitionId, UUID rulebookId, long version,
        GameSystemDefinitionStatus status, String definitionJson, Instant publishedAt) {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public GameSystemDefinitionRevision {
        Objects.requireNonNull(definitionId); Objects.requireNonNull(rulebookId); Objects.requireNonNull(status);
        if (version < 1) throw new IllegalArgumentException("definition version must be positive");
        definitionJson = normalize(definitionJson);
        if (status == GameSystemDefinitionStatus.PUBLISHED && publishedAt == null) publishedAt = Instant.now();
    }

    public static GameSystemDefinitionRevision draft(UUID rulebookId, long version, String definitionJson) {
        return new GameSystemDefinitionRevision(UUID.randomUUID(), rulebookId, version,
                GameSystemDefinitionStatus.DRAFT, definitionJson, null);
    }

    public GameSystemDefinitionRevision publish() {
        if (status == GameSystemDefinitionStatus.PUBLISHED) throw new IllegalStateException("definition is already published");
        return new GameSystemDefinitionRevision(definitionId, rulebookId, version,
                GameSystemDefinitionStatus.PUBLISHED, definitionJson, Instant.now());
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("definition JSON must not be blank");
        try {
            JsonNode node = MAPPER.readTree(value);
            if (!node.isObject()) throw new IllegalArgumentException("definition JSON must be an object");
            return MAPPER.writeValueAsString(node);
        } catch (IllegalArgumentException e) { throw e; }
        catch (Exception e) { throw new IllegalArgumentException("definition JSON is invalid", e); }
    }
}
