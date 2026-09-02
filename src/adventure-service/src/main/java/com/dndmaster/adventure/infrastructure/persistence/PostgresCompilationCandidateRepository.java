package com.dndmaster.adventure.infrastructure.persistence;

import com.dndmaster.adventure.application.scenario.compilation.CompilationCandidateRepository;
import com.dndmaster.adventure.domain.scenario.CandidateRecoverability;
import com.dndmaster.adventure.domain.scenario.CandidateValidation;
import com.dndmaster.adventure.domain.scenario.CompilationCandidate;
import com.dndmaster.adventure.domain.scenario.CandidateCompleteness;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;

public final class PostgresCompilationCandidateRepository implements CompilationCandidateRepository {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final DataSource dataSource;

    public PostgresCompilationCandidateRepository(DataSource dataSource) {
        this.dataSource = java.util.Objects.requireNonNull(dataSource, "data source must not be null");
    }

    @Override
    public void saveAll(UUID compilationId, List<CompilationCandidate> candidates) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement delete = connection.prepareStatement(
                    "DELETE FROM scenario_compilation_candidate WHERE compilation_id = ?")) {
                delete.setObject(1, compilationId);
                delete.executeUpdate();
            }
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO scenario_compilation_candidate
                    (candidate_id, compilation_id, candidate_key, candidate_type, required, completeness,
                     validation_json, recoverability, repair_attempt_count, raw_resolution_ref, final_resolution_ref,
                     created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?)
                    """)) {
                for (CompilationCandidate candidate : candidates) {
                    insert.setObject(1, candidate.candidateId()); insert.setObject(2, candidate.compilationId());
                    insert.setString(3, candidate.candidateKey()); insert.setString(4, candidate.candidateType());
                    insert.setBoolean(5, candidate.required()); insert.setString(6, candidate.completeness().name());
                    insert.setString(7, JSON.writeValueAsString(candidate.validations()));
                    insert.setString(8, candidate.recoverability().name()); insert.setInt(9, candidate.repairAttemptCount());
                    insert.setString(10, candidate.rawResolutionRef()); insert.setString(11, candidate.finalResolutionRef());
                    insert.setTimestamp(12, Timestamp.from(candidate.createdAt())); insert.setTimestamp(13, Timestamp.from(candidate.updatedAt()));
                    insert.addBatch();
                }
                insert.executeBatch();
            }
            connection.commit();
        } catch (Exception exception) {
            throw new ScenarioPackagePersistenceException("could not save compilation candidates", exception);
        }
    }

    @Override
    public List<CompilationCandidate> findByCompilationId(UUID compilationId) {
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement("""
                SELECT candidate_id, compilation_id, candidate_key, candidate_type, required, completeness,
                       validation_json, recoverability, repair_attempt_count, raw_resolution_ref, final_resolution_ref,
                       created_at, updated_at
                FROM scenario_compilation_candidate WHERE compilation_id = ? ORDER BY candidate_key
                """)) {
            statement.setObject(1, compilationId);
            List<CompilationCandidate> result = new ArrayList<>();
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) result.add(new CompilationCandidate(
                        rows.getObject("candidate_id", UUID.class), rows.getObject("compilation_id", UUID.class),
                        rows.getString("candidate_key"), rows.getString("candidate_type"), rows.getBoolean("required"),
                        CandidateCompleteness.valueOf(rows.getString("completeness")),
                        JSON.readValue(rows.getString("validation_json"), new TypeReference<List<CandidateValidation>>() {}),
                        CandidateRecoverability.valueOf(rows.getString("recoverability")), rows.getInt("repair_attempt_count"),
                        rows.getString("raw_resolution_ref"), rows.getString("final_resolution_ref"),
                        rows.getTimestamp("created_at").toInstant(), rows.getTimestamp("updated_at").toInstant()));
            }
            return List.copyOf(result);
        } catch (Exception exception) {
            throw new ScenarioPackagePersistenceException("could not load compilation candidates", exception);
        }
    }
}
