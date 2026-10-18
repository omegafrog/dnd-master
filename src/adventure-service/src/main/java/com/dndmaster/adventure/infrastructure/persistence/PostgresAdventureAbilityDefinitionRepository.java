package com.dndmaster.adventure.infrastructure.persistence;

import com.dndmaster.adventure.application.ability.AdventureAbilityDefinitionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

public final class PostgresAdventureAbilityDefinitionRepository implements AdventureAbilityDefinitionRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public PostgresAdventureAbilityDefinitionRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Override
    public Optional<StoredAbilityDefinition> find(UUID adventureId, String abilityId) {
        return jdbc.query("SELECT status, definition_json::text, revision FROM adventure_ability_definition WHERE adventure_id=? AND ability_id=?",
                row -> row.next() ? Optional.of(read(row.getString(2), row.getString(1), row.getLong(3))) : Optional.empty(),
                adventureId, abilityId);
    }

    @Override
    public StoredAbilityDefinition prepare(UUID adventureId, String abilityId, JsonNode definition) {
        String json = write(definition);
        jdbc.update("INSERT INTO adventure_ability_definition(adventure_id, ability_id, status, definition_json) VALUES (?, ?, 'PREPARING', ?::jsonb) ON CONFLICT (adventure_id, ability_id) DO NOTHING",
                adventureId, abilityId, json);
        return find(adventureId, abilityId).orElseThrow();
    }

    @Override
    public void activate(UUID adventureId, String abilityId) {
        jdbc.update("UPDATE adventure_ability_definition SET status='ACTIVE', revision=revision+1, updated_at=now() WHERE adventure_id=? AND ability_id=? AND status='PREPARING'",
                adventureId, abilityId);
    }

    private StoredAbilityDefinition read(String json, String status, long revision) {
        try { return new StoredAbilityDefinition(status, mapper.readTree(json), revision); }
        catch (JsonProcessingException failure) { throw new IllegalStateException("stored ability definition is invalid", failure); }
    }

    private String write(JsonNode definition) {
        try { return mapper.writeValueAsString(definition); }
        catch (JsonProcessingException failure) { throw new IllegalArgumentException("ability definition cannot be serialized", failure); }
    }
}
