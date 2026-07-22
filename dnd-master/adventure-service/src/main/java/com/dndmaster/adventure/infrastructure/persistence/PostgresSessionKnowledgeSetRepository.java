package com.dndmaster.adventure.infrastructure.persistence;

import com.dndmaster.adventure.application.knowledge.SessionKnowledgeSetRepository;
import com.dndmaster.adventure.domain.adventure.SessionId;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.domain.knowledge.SessionKnowledgeSet;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

public final class PostgresSessionKnowledgeSetRepository implements SessionKnowledgeSetRepository {
    private final DataSource dataSource;

    public PostgresSessionKnowledgeSetRepository(DataSource dataSource) {
        this.dataSource = java.util.Objects.requireNonNull(dataSource, "dataSource must not be null");
    }

    @Override
    public Optional<SessionKnowledgeSet> findBySessionId(SessionId sessionId) {
        String sql = """
                SELECT knowledge_document_id
                  FROM adventure_session_knowledge_document
                 WHERE session_id = ?
                 ORDER BY selection_order
                """;
        List<KnowledgeDocumentId> ids = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, sessionId.value());
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    ids.add(new KnowledgeDocumentId(rows.getObject(1, UUID.class)));
                }
            }
        } catch (SQLException exception) {
            throw failure("could not load session knowledge set", exception);
        }
        if (ids.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new SessionKnowledgeSet(sessionId, ids));
    }

    @Override
    public void save(SessionKnowledgeSet set) {
        try (Connection connection = dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement delete = connection.prepareStatement(
                        "DELETE FROM adventure_session_knowledge_document WHERE session_id = ?")) {
                    delete.setObject(1, set.sessionId().value());
                    delete.executeUpdate();
                }
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO adventure_session_knowledge_document(session_id, selection_order, knowledge_document_id) VALUES (?, ?, ?)")) {
                    for (int index = 0; index < set.knowledgeDocumentIds().size(); index++) {
                        insert.setObject(1, set.sessionId().value());
                        insert.setInt(2, index);
                        insert.setObject(3, set.knowledgeDocumentIds().get(index).value());
                        insert.addBatch();
                    }
                    insert.executeBatch();
                }
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw failure("could not save session knowledge set", exception);
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        } catch (SQLException exception) {
            throw failure("could not access session knowledge storage", exception);
        }
    }

    private static void rollback(Connection connection, Throwable original) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }

    private static SessionKnowledgeSetPersistenceException failure(String message, Throwable cause) {
        return new SessionKnowledgeSetPersistenceException(message, cause);
    }
}
