package com.dndmaster.ruleknowledge.infrastructure.persistence;

import com.dndmaster.ruleknowledge.application.preprocessing.PreprocessingRetryLeaseRepository;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookId;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.Optional;
import javax.sql.DataSource;

public final class PostgresPreprocessingRetryLeaseRepository implements PreprocessingRetryLeaseRepository {
    private static final String CLAIM = """
            INSERT INTO rag_extraction_retry
                (document_id, request_id, candidate_version, pages, lease_token, lease_until, status)
            VALUES (?, ?, ?, ?, ?, now() + (? * interval '1 millisecond'), 'LEASED')
            ON CONFLICT (document_id, request_id) DO UPDATE SET
                candidate_version = EXCLUDED.candidate_version, pages = EXCLUDED.pages,
                lease_token = EXCLUDED.lease_token, lease_until = EXCLUDED.lease_until,
                status = 'LEASED', updated_at = now()
             WHERE rag_extraction_retry.status <> 'COMPLETED'
                OR rag_extraction_retry.lease_until <= now()
            RETURNING lease_token, status, result_version
            """;
    private static final String READ = "SELECT lease_token, status, result_version FROM rag_extraction_retry WHERE document_id = ? AND request_id = ?";
    private static final String COMPLETE = """
            UPDATE rag_extraction_retry
               SET status = 'COMPLETED', result_version = ?, updated_at = now()
             WHERE document_id = ? AND request_id = ? AND lease_token = ?
               AND candidate_version = ? AND lease_until > now() AND status = 'LEASED'
            """;
    private static final String RELEASE = "DELETE FROM rag_extraction_retry WHERE document_id = ? AND request_id = ? AND lease_token = ?";

    private final DataSource dataSource;

    public PostgresPreprocessingRetryLeaseRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "data source must not be null");
    }

    @Override
    public RetryClaim claim(RulebookId documentId, String requestId, String candidateVersion, List<Integer> pages, Duration lease) {
        String token = UUID.randomUUID().toString();
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(CLAIM)) {
                statement.setObject(1, documentId.value(), Types.OTHER);
                statement.setString(2, requestId);
                statement.setString(3, candidateVersion);
                statement.setArray(4, connection.createArrayOf("integer", pages.toArray(Integer[]::new)));
                statement.setString(5, token);
                statement.setLong(6, lease.toMillis());
                try (ResultSet row = statement.executeQuery()) {
                    if (row.next()) return new RetryClaim(token.equals(row.getString("lease_token")),
                            "COMPLETED".equals(row.getString("status")), row.getString("lease_token"), row.getString("result_version"));
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(READ)) {
                statement.setObject(1, documentId.value(), Types.OTHER);
                statement.setString(2, requestId);
                try (ResultSet row = statement.executeQuery()) {
                    if (!row.next()) throw new IllegalStateException("retry lease claim was not persisted");
                    return new RetryClaim(false, "COMPLETED".equals(row.getString("status")),
                            row.getString("lease_token"), row.getString("result_version"));
                }
            }
        } catch (SQLException exception) {
            throw new RuleVectorPersistenceException("could not claim preprocessing retry", exception);
        }
    }

    @Override
    public boolean complete(RulebookId documentId, String requestId, String leaseToken, String candidateVersion, String resultVersion) {
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(COMPLETE)) {
            statement.setString(1, resultVersion);
            statement.setObject(2, documentId.value(), Types.OTHER);
            statement.setString(3, requestId);
            statement.setString(4, leaseToken);
            statement.setString(5, candidateVersion);
            return statement.executeUpdate() == 1;
        } catch (SQLException exception) {
            throw new RuleVectorPersistenceException("could not complete preprocessing retry", exception);
        }
    }

    @Override
    public void release(RulebookId documentId, String requestId, String leaseToken) {
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(RELEASE)) {
            statement.setObject(1, documentId.value(), Types.OTHER);
            statement.setString(2, requestId);
            statement.setString(3, leaseToken);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new RuleVectorPersistenceException("could not release preprocessing retry", exception);
        }
    }

    @Override
    public Optional<String> completedResult(RulebookId documentId, String requestId) {
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(READ)) {
            statement.setObject(1, documentId.value(), Types.OTHER);
            statement.setString(2, requestId);
            try (ResultSet row = statement.executeQuery()) {
                return row.next() && "COMPLETED".equals(row.getString("status"))
                        ? Optional.ofNullable(row.getString("result_version")) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new RuleVectorPersistenceException("could not read preprocessing retry", exception);
        }
    }
}
