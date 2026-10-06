package com.dndmaster.adventure.infrastructure.persistence;

import com.dndmaster.adventure.application.combat.EnemyCharacterSheet;
import com.dndmaster.adventure.application.combat.EnemyCharacterSheetIdentity;
import com.dndmaster.adventure.application.combat.EnemyCharacterSheetRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

public final class PostgresEnemyCharacterSheetRepository implements EnemyCharacterSheetRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    public PostgresEnemyCharacterSheetRepository(DataSource dataSource, ObjectMapper objectMapper) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.objectMapper = java.util.Objects.requireNonNull(objectMapper);
    }
    @Override public Optional<EnemyCharacterSheet> find(EnemyCharacterSheetIdentity identity) {
        return jdbc.query("SELECT sheet_json::text FROM enemy_character_sheet WHERE identity_key = ?",
                (rs, row) -> read(rs.getString(1)), identity.identityKey()).stream().findFirst();
    }
    @Override public EnemyCharacterSheet saveIfAbsent(EnemyCharacterSheet sheet) {
        try {
            jdbc.update("INSERT INTO enemy_character_sheet(identity_key, adventure_id, sheet_json) VALUES (?, ?, ?::jsonb) ON CONFLICT(identity_key) DO NOTHING",
                    sheet.identity().identityKey(), sheet.identity().adventureId(), objectMapper.writeValueAsString(sheet));
            return find(sheet.identity()).orElseThrow(() -> new IllegalStateException("saved enemy sheet could not be read"));
        } catch (Exception e) {
            throw new EnemyCharacterSheetPersistenceException("could not save verified enemy sheet", e);
        }
    }
    private EnemyCharacterSheet read(String json) {
        try { return objectMapper.readValue(json, EnemyCharacterSheet.class); }
        catch (Exception e) { throw new EnemyCharacterSheetPersistenceException("stored enemy sheet is invalid", e); }
    }
}
