package com.dndmaster.ruleknowledge.infrastructure.persistence;

import com.dndmaster.ruleknowledge.application.publication.EmbeddedPublishedRagChunk;
import com.dndmaster.ruleknowledge.application.publication.ExtractionPublicationStatus;
import com.dndmaster.ruleknowledge.application.publication.RagExtractionPage;
import com.dndmaster.ruleknowledge.application.publication.RagExtractionPublicationRepository;
import com.dndmaster.ruleknowledge.application.publication.RagExtractionPublicationRequest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Objects;
import javax.sql.DataSource;

/** PostgreSQL boundary for immutable extraction candidates and their public pointer. */
public final class PostgresRagExtractionPublicationRepository implements RagExtractionPublicationRepository {
    private static final String INSERT_VERSION = """
            INSERT INTO rag_extraction_version
                (document_id, extraction_version, owner_player_id, operation_id, source_hash,
                 policy_version, manifest_hash, status)
            VALUES (?, ?, ?, ?, ?, ?, ?, 'INDEXING')
            ON CONFLICT (document_id, extraction_version) DO NOTHING
            """;
    private static final String SELECT_VERSION = """
            SELECT owner_player_id, operation_id, source_hash, policy_version, manifest_hash
              FROM rag_extraction_version
             WHERE document_id = ? AND extraction_version = ?
             FOR UPDATE
            """;
    private static final String SELECT_DOCUMENT_OWNER = """
            SELECT owner_player_id
              FROM rulebook_registration
             WHERE rulebook_id = ?
            """;
    private static final String INSERT_PAGE = """
            INSERT INTO rag_extraction_page
                (document_id, extraction_version, page_number, status, attempts, findings)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (document_id, extraction_version, page_number) DO UPDATE SET
                status = EXCLUDED.status, attempts = EXCLUDED.attempts, findings = EXCLUDED.findings
            """;
    private static final String SELECT_PAGE_COUNTS = """
            SELECT COUNT(*) AS total_pages,
                   COUNT(*) FILTER (WHERE status = 'VALIDATED') AS validated_pages
              FROM rag_extraction_page
             WHERE document_id = ? AND extraction_version = ?
            """;
    private static final String INSERT_CHUNK = """
            INSERT INTO published_rag_chunk
                (document_id, owner_player_id, extraction_version, processor_chunk_id, chunk_id,
                 sequence, content, embedding_text, embedding, embedding_model, embedding_dimension,
                 section_path, page_number, bbox, table_cell, original_locator)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS vector), ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (document_id, extraction_version, processor_chunk_id) DO UPDATE SET
                chunk_id = EXCLUDED.chunk_id, sequence = EXCLUDED.sequence, content = EXCLUDED.content,
                embedding_text = EXCLUDED.embedding_text, embedding = EXCLUDED.embedding,
                embedding_model = EXCLUDED.embedding_model, embedding_dimension = EXCLUDED.embedding_dimension,
                section_path = EXCLUDED.section_path, page_number = EXCLUDED.page_number,
                bbox = EXCLUDED.bbox, table_cell = EXCLUDED.table_cell,
                original_locator = EXCLUDED.original_locator
            """;
    private static final String MARK_INDEXED = """
            UPDATE rag_extraction_version
               SET status = 'INDEXED', failure_reason = NULL, updated_at = now()
             WHERE document_id = ? AND extraction_version = ?
            """;
    private static final String SWITCH_PUBLIC_POINTER = """
            UPDATE rulebook_registration
               SET published_extraction_version = ?, updated_at = now()
             WHERE rulebook_id = ? AND owner_player_id = ?
            """;
    private static final String MARK_FAILED = """
            UPDATE rag_extraction_version
               SET status = ?, failure_reason = ?, updated_at = now()
             WHERE document_id = ? AND extraction_version = ?
            """;

    private final DataSource dataSource;

    public PostgresRagExtractionPublicationRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
    }

    @Override
    public void beginCandidate(RagExtractionPublicationRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        inTransaction(connection -> {
            verifyDocumentIdentity(connection, request);
            try (PreparedStatement insert = connection.prepareStatement(INSERT_VERSION)) {
                setUuid(insert, 1, request.documentId().value());
                insert.setString(2, request.extractionVersion());
                setUuid(insert, 3, request.ownerPlayerId().value());
                insert.setString(4, request.operationId());
                insert.setString(5, request.sourceHash());
                insert.setString(6, request.policyVersion());
                insert.setString(7, request.manifestHash());
                insert.executeUpdate();
            }
            verifyVersionIdentity(connection, request);
            try (PreparedStatement insertPage = connection.prepareStatement(INSERT_PAGE)) {
                for (RagExtractionPage page : request.pages()) {
                    setUuid(insertPage, 1, request.documentId().value());
                    insertPage.setString(2, request.extractionVersion());
                    insertPage.setInt(3, page.pageNumber());
                    insertPage.setString(4, page.status());
                    insertPage.setInt(5, page.attempts());
                    setTextArray(insertPage, 6, connection, page.findings());
                    insertPage.addBatch();
                }
                insertPage.executeBatch();
            }
        });
    }

    @Override
    public void publish(RagExtractionPublicationRequest request, List<EmbeddedPublishedRagChunk> chunks) {
        Objects.requireNonNull(request, "request must not be null");
        List<EmbeddedPublishedRagChunk> immutableChunks = List.copyOf(Objects.requireNonNull(chunks, "chunks must not be null"));
        if (immutableChunks.isEmpty()) throw new IllegalArgumentException("published chunks must not be empty");
        inTransaction(connection -> {
            verifyDocumentIdentity(connection, request);
            verifyVersionIdentity(connection, request);
            verifyAllPagesValidated(connection, request);
            try (PreparedStatement insert = connection.prepareStatement(INSERT_CHUNK)) {
                for (EmbeddedPublishedRagChunk embedded : immutableChunks) {
                    var chunk = embedded.chunk();
                    var provenance = chunk.provenance();
                    setUuid(insert, 1, request.documentId().value());
                    setUuid(insert, 2, request.ownerPlayerId().value());
                    insert.setString(3, request.extractionVersion());
                    insert.setString(4, chunk.processorChunkId());
                    setUuid(insert, 5, com.dndmaster.ruleknowledge.domain.index.ChunkId
                            .fromStableValue(chunk.processorChunkId()).value());
                    insert.setInt(6, chunk.sequence());
                    insert.setString(7, chunk.content());
                    insert.setString(8, chunk.embeddingText());
                    insert.setString(9, vectorLiteral(embedded.embedding()));
                    insert.setString(10, request.embeddingModel());
                    insert.setInt(11, embedded.embedding().length);
                    setTextArray(insert, 12, connection, provenance.sectionPath());
                    insert.setInt(13, provenance.pageNumber());
                    setDoubleArray(insert, 14, connection, provenance.bbox());
                    if (provenance.tableCell() == null) insert.setNull(15, Types.VARCHAR);
                    else insert.setString(15, provenance.tableCell());
                    insert.setString(16, provenance.originalLocator());
                    insert.addBatch();
                }
                insert.executeBatch();
            }
            executeUpdate(connection, MARK_INDEXED, statement -> {
                statement.setObject(1, request.documentId().value(), Types.OTHER);
                statement.setString(2, request.extractionVersion());
            }, "could not mark extraction version indexed");
            int switched = executeUpdate(connection, SWITCH_PUBLIC_POINTER, statement -> {
                statement.setString(1, request.extractionVersion());
                setUuid(statement, 2, request.documentId().value());
                setUuid(statement, 3, request.ownerPlayerId().value());
            }, "document identity is not registered for publication");
            if (switched != 1) throw new IllegalStateException("document identity is not registered for publication");
        });
    }

    @Override
    public void fail(RagExtractionPublicationRequest request, ExtractionPublicationStatus status, String reason) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(status, "status must not be null");
        if (status != ExtractionPublicationStatus.FAILED && status != ExtractionPublicationStatus.NEEDS_REVIEW) {
            throw new IllegalArgumentException("only failed or needs-review candidates can be marked failed");
        }
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(MARK_FAILED)) {
            statement.setString(1, status.name());
            statement.setString(2, reason);
            setUuid(statement, 3, request.documentId().value());
            statement.setString(4, request.extractionVersion());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new RuleVectorPersistenceException("could not mark extraction publication failed", exception);
        }
    }

    private void verifyVersionIdentity(Connection connection, RagExtractionPublicationRequest request) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_VERSION)) {
            setUuid(statement, 1, request.documentId().value());
            statement.setString(2, request.extractionVersion());
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) throw new IllegalStateException("extraction candidate does not exist");
                if (!request.ownerPlayerId().value().equals(row.getObject("owner_player_id"))
                        || !request.operationId().equals(row.getString("operation_id"))
                        || !request.sourceHash().equals(row.getString("source_hash"))
                        || !request.policyVersion().equals(row.getString("policy_version"))
                        || !request.manifestHash().equals(row.getString("manifest_hash"))) {
                    throw new IllegalArgumentException("extraction candidate identity does not match request");
                }
            }
        }
    }

    private static void verifyDocumentIdentity(Connection connection, RagExtractionPublicationRequest request)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_DOCUMENT_OWNER)) {
            setUuid(statement, 1, request.documentId().value());
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next() || !request.ownerPlayerId().value().equals(row.getObject("owner_player_id"))) {
                    throw new IllegalArgumentException("document identity does not match publication owner");
                }
            }
        }
    }

    private static void verifyAllPagesValidated(Connection connection, RagExtractionPublicationRequest request) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_PAGE_COUNTS)) {
            setUuid(statement, 1, request.documentId().value());
            statement.setString(2, request.extractionVersion());
            try (ResultSet row = statement.executeQuery()) {
                row.next();
                if (row.getInt("total_pages") != request.pages().size()
                        || row.getInt("validated_pages") != request.pages().size()) {
                    throw new IllegalStateException("extraction version contains an unvalidated or missing page");
                }
            }
        }
    }

    private void inTransaction(SqlWork work) {
        try (Connection connection = dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                work.run(connection);
                connection.commit();
            } catch (Exception exception) {
                try { connection.rollback(); } catch (SQLException rollback) { exception.addSuppressed(rollback); }
                if (exception instanceof RuntimeException runtime) throw runtime;
                throw new RuleVectorPersistenceException("could not persist extraction publication", exception);
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        } catch (SQLException exception) {
            throw new RuleVectorPersistenceException("could not access extraction publication storage", exception);
        }
    }

    private static int executeUpdate(Connection connection, String sql, StatementBinder binder, String message)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            return statement.executeUpdate();
        } catch (SQLException exception) {
            throw new RuleVectorPersistenceException(message, exception);
        }
    }

    private static void setUuid(PreparedStatement statement, int index, java.util.UUID value) throws SQLException {
        statement.setObject(index, value, Types.OTHER);
    }

    private static void setTextArray(PreparedStatement statement, int index, Connection connection, List<String> values)
            throws SQLException {
        statement.setArray(index, connection.createArrayOf("text", values.toArray(String[]::new)));
    }

    private static void setDoubleArray(PreparedStatement statement, int index, Connection connection, List<Double> values)
            throws SQLException {
        if (values == null || values.isEmpty()) {
            statement.setNull(index, Types.ARRAY);
            return;
        }
        statement.setArray(index, connection.createArrayOf("float8", values.toArray(Double[]::new)));
    }

    private static String vectorLiteral(float[] values) {
        StringBuilder result = new StringBuilder("[");
        for (int index = 0; index < values.length; index++) {
            if (index > 0) result.append(',');
            result.append(values[index]);
        }
        return result.append(']').toString();
    }

    @FunctionalInterface
    private interface SqlWork { void run(Connection connection) throws Exception; }

    @FunctionalInterface
    private interface StatementBinder { void bind(PreparedStatement statement) throws SQLException; }
}
