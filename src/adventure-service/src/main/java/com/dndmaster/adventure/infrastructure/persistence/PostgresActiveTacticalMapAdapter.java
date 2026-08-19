package com.dndmaster.adventure.infrastructure.persistence;

import com.dndmaster.adventure.application.runtime.ActiveTacticalMapPort;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/** Durable stage-owned map binding; deliberately never falls back to another stage. */
public final class PostgresActiveTacticalMapAdapter implements ActiveTacticalMapPort {
    private final JdbcTemplate jdbc;

    public PostgresActiveTacticalMapAdapter(DataSource dataSource) { this.jdbc = new JdbcTemplate(dataSource); }

    @Override
    public Optional<UUID> findActiveMap(UUID adventureId, int stagePosition, UUID ownerPlayerId) {
        return jdbc.query("SELECT combat_map_id FROM adventure_active_tactical_map WHERE adventure_id = ? AND stage_position = ? AND owner_player_id = ?",
                (rs, row) -> rs.getObject("combat_map_id", UUID.class), adventureId, stagePosition, ownerPlayerId).stream().findFirst();
    }

    @Override
    public void bindActiveMap(UUID adventureId, int stagePosition, UUID ownerPlayerId, UUID combatMapId) {
        jdbc.update("INSERT INTO adventure_active_tactical_map(adventure_id, stage_position, owner_player_id, combat_map_id) VALUES (?, ?, ?, ?) "
                        + "ON CONFLICT (adventure_id, stage_position, owner_player_id) DO UPDATE SET combat_map_id = EXCLUDED.combat_map_id",
                adventureId, stagePosition, ownerPlayerId, combatMapId);
    }
}
