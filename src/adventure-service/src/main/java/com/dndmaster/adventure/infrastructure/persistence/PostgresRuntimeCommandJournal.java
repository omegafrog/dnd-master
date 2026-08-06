package com.dndmaster.adventure.infrastructure.persistence;

import com.dndmaster.adventure.application.runtime.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

public final class PostgresRuntimeCommandJournal implements RuntimeCommandJournal {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public PostgresRuntimeCommandJournal(DataSource dataSource, ObjectMapper mapper) {
        this.jdbc = new JdbcTemplate(dataSource); this.mapper = mapper;
    }

    @Override
    public Optional<RuntimeCommandJournalEntry> find(UUID commandId) {
        return jdbc.query("SELECT command_id, session_id, turn_id, owner_player_id, tool_name, fingerprint, status, outcome_json, version FROM adventure_runtime_command_journal WHERE command_id = ?",
                this::map, commandId).stream().findFirst();
    }

    @Override
    public boolean claim(RuntimeCommandJournalEntry entry) {
        return jdbc.update("INSERT INTO adventure_runtime_command_journal(command_id, session_id, turn_id, owner_player_id, tool_name, fingerprint, status, version) VALUES (?, ?, ?, ?, ?, ?, 'PENDING', 0) ON CONFLICT (command_id) DO NOTHING",
                entry.commandId(), entry.sessionId(), entry.turnId(), entry.ownerPlayerId(), entry.toolName(), entry.fingerprint()) == 1;
    }

    @Override
    public void record(RuntimeCommandJournalEntry entry) {
        int updated = jdbc.update("UPDATE adventure_runtime_command_journal SET status = ?, outcome_json = ?, version = ?, updated_at = CURRENT_TIMESTAMP WHERE command_id = ? AND fingerprint = ? AND version = ?",
                entry.status().name(), outcome(entry.outcome()), entry.version(), entry.commandId(), entry.fingerprint(), entry.version() - 1);
        if (updated == 0 && !find(entry.commandId()).map(current -> current.status() == entry.status() && current.version() == entry.version()).orElse(false)) {
            throw new IllegalStateException("runtime command journal version conflict");
        }
    }

    private RuntimeCommandJournalEntry map(ResultSet row, int ignored) throws java.sql.SQLException {
        try {
            String json = row.getString("outcome_json");
            RuntimeCommandOutcome outcome = json == null ? null : mapper.readValue(json, RuntimeCommandOutcome.class);
            return new RuntimeCommandJournalEntry(row.getObject("command_id", UUID.class), row.getObject("session_id", UUID.class), row.getObject("turn_id", UUID.class), row.getObject("owner_player_id", UUID.class), row.getString("tool_name"), row.getString("fingerprint"), RuntimeCommandStatus.valueOf(row.getString("status")), outcome, row.getLong("version"));
        } catch (JsonProcessingException e) { throw new IllegalStateException("invalid runtime command outcome", e); }
    }

    private String outcome(RuntimeCommandOutcome value) {
        if (value == null) return null;
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalStateException("cannot serialize runtime command outcome", e); }
    }
}
