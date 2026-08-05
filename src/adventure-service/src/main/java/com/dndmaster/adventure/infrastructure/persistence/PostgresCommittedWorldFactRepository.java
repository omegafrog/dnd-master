package com.dndmaster.adventure.infrastructure.persistence;

import com.dndmaster.adventure.application.runtime.CommittedWorldFactRepository;
import com.dndmaster.adventure.domain.runtime.fact.CommittedWorldFact;
import com.dndmaster.adventure.domain.runtime.fact.CommittedWorldFactLedger;
import com.dndmaster.adventure.domain.runtime.fact.FactVisibility;
import java.sql.SQLException;
import java.util.UUID;
import javax.sql.DataSource;

public final class PostgresCommittedWorldFactRepository implements CommittedWorldFactRepository {
    private final DataSource dataSource;
    public PostgresCommittedWorldFactRepository(DataSource dataSource) { this.dataSource = dataSource; }
    public CommittedWorldFactLedger findBySessionId(UUID sessionId) {
        var result = CommittedWorldFactLedger.empty();
        try (var c = dataSource.getConnection(); var s = c.prepareStatement("SELECT fact_id,fact_version,subject,predicate,object_value,visibility,provenance,cause_turn_id FROM committed_world_fact WHERE session_id=? ORDER BY fact_version")) {
            s.setObject(1, sessionId);
            try (var rows = s.executeQuery()) { while (rows.next()) result = result.append(new CommittedWorldFact(rows.getObject(1, UUID.class), rows.getString(3), rows.getString(4), rows.getString(5), FactVisibility.valueOf(rows.getString(6)), rows.getString(7), rows.getObject(8, UUID.class), rows.getLong(2))); }
            return result;
        } catch (SQLException e) { throw new AdventurePersistenceException("could not load committed world facts", e); }
    }
    public void append(UUID sessionId, CommittedWorldFact fact) {
        findBySessionId(sessionId).append(fact);
        try (var c = dataSource.getConnection(); var s = c.prepareStatement("INSERT INTO committed_world_fact(fact_id,session_id,fact_version,subject,predicate,object_value,visibility,provenance,cause_turn_id,committed_at) VALUES (?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP) ON CONFLICT(fact_id) DO NOTHING")) {
            s.setObject(1, fact.factId()); s.setObject(2, sessionId); s.setLong(3, fact.version()); s.setString(4, fact.subject()); s.setString(5, fact.predicate()); s.setString(6, fact.object()); s.setString(7, fact.visibility().name()); s.setString(8, fact.provenance()); s.setObject(9, fact.causeTurnId()); s.executeUpdate();
        } catch (SQLException e) { throw new AdventurePersistenceException("could not append committed world fact", e); }
    }
}
