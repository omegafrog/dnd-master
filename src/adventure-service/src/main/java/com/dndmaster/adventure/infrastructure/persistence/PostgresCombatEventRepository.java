package com.dndmaster.adventure.infrastructure.persistence;

import com.dndmaster.adventure.application.combat.CombatEventRepository;
import com.dndmaster.adventure.domain.combat.CombatEvent;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

public final class PostgresCombatEventRepository implements CombatEventRepository {
    private final JdbcTemplate jdbc;
    public PostgresCombatEventRepository(DataSource dataSource) { this.jdbc = new JdbcTemplate(dataSource); }
    @Override public void append(CombatEvent event) {
        jdbc.update("INSERT INTO combat_event(encounter_id, sequence, event_type, player_payload) VALUES (?, ?, ?, ?::jsonb) ON CONFLICT DO NOTHING",
                event.encounterId(), event.sequence(), event.eventType(), event.playerPayload());
    }
    @Override public List<CombatEvent> after(UUID encounterId, long sequence) {
        return jdbc.query("SELECT sequence, event_type, player_payload::text FROM combat_event WHERE encounter_id = ? AND sequence > ? ORDER BY sequence",
                (rs, row) -> new CombatEvent(encounterId, rs.getLong(1), rs.getString(2), rs.getString(3)), encounterId, sequence);
    }
}
