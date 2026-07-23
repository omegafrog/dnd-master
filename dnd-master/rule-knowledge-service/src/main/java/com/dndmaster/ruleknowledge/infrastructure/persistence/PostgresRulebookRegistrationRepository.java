package com.dndmaster.ruleknowledge.infrastructure.persistence;

import com.dndmaster.ruleknowledge.application.registration.RulebookRegistrationRepository;
import com.dndmaster.ruleknowledge.application.registration.StoredRulebookRegistration;
import com.dndmaster.ruleknowledge.domain.rulebook.*;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

public final class PostgresRulebookRegistrationRepository implements RulebookRegistrationRepository {
    private static final String FIND_BY_ID = """
            SELECT rulebook_id, owner_player_id, operation_key, content_hash, format, file_size,
                   storage_key, processing_status, extraction_status, extracted_content,
                   missing_locations, failure_code, version, created_at, updated_at,
                   document_type, original_filename
              FROM rulebook_registration WHERE rulebook_id = ?
            """;
    private static final String FIND_BY_OPERATION_KEY = """
            SELECT rulebook_id, owner_player_id, operation_key, content_hash, format, file_size,
                   storage_key, processing_status, extraction_status, extracted_content,
                   missing_locations, failure_code, version, created_at, updated_at,
                   document_type, original_filename
              FROM rulebook_registration WHERE operation_key = ? OR operation_key LIKE ? ESCAPE '\\'
            """;
    private static final String FIND_BY_OWNER_AND_CONTENT_HASH = """
            SELECT rulebook_id, owner_player_id, operation_key, content_hash, format, file_size,
                   storage_key, processing_status, extraction_status, extracted_content,
                   missing_locations, failure_code, version, created_at, updated_at,
                   document_type, original_filename
              FROM rulebook_registration WHERE owner_player_id = ? AND content_hash = ?
            """;
    private static final String FIND_BY_OWNER = """
            SELECT rulebook_id, owner_player_id, operation_key, content_hash, format, file_size,
                   storage_key, processing_status, extraction_status, extracted_content,
                   missing_locations, failure_code, version, created_at, updated_at,
                   document_type, original_filename
              FROM rulebook_registration WHERE owner_player_id = ?
              ORDER BY created_at DESC
            """;
    private static final String FIND_BY_STATUS_PREFIX = """
            SELECT rulebook_id, owner_player_id, operation_key, content_hash, format, file_size,
                   storage_key, processing_status, extraction_status, extracted_content,
                   missing_locations, failure_code, version, created_at, updated_at,
                   document_type, original_filename
              FROM rulebook_registration WHERE processing_status IN (
            """;
    private static final String CLAIM_PENDING = """
            WITH claimed AS (
                SELECT rulebook_id
                  FROM rulebook_registration
                 WHERE (
                       processing_status IN ('QUEUED', 'UPLOADED', 'EXTRACTED')
                    OR (processing_status = 'PROCESSING' AND updated_at < ?)
                 )
                 ORDER BY created_at ASC
                 FOR UPDATE SKIP LOCKED
                 LIMIT ?
            )
            UPDATE rulebook_registration registration
               SET processing_status = 'PROCESSING',
                   version = registration.version + 1,
                   updated_at = now()
              FROM claimed
             WHERE registration.rulebook_id = claimed.rulebook_id
            RETURNING registration.rulebook_id, registration.owner_player_id, registration.operation_key,
                      registration.content_hash, registration.format, registration.file_size,
                      registration.storage_key, registration.processing_status, registration.extraction_status,
                      registration.extracted_content, registration.missing_locations, registration.failure_code,
                      registration.version, registration.created_at, registration.updated_at,
                      registration.document_type, registration.original_filename
            """;
    private static final String UPSERT = """
            INSERT INTO rulebook_registration
                (rulebook_id, owner_player_id, operation_key, content_hash, format, file_size,
                 storage_key, processing_status, extraction_status, extracted_content,
                 missing_locations, failure_code, version, created_at, updated_at,
                 document_type, original_filename)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (rulebook_id) DO UPDATE SET
                owner_player_id = EXCLUDED.owner_player_id,
                operation_key = EXCLUDED.operation_key,
                content_hash = EXCLUDED.content_hash,
                format = EXCLUDED.format,
                file_size = EXCLUDED.file_size,
                storage_key = EXCLUDED.storage_key,
                processing_status = EXCLUDED.processing_status,
                extraction_status = EXCLUDED.extraction_status,
                extracted_content = EXCLUDED.extracted_content,
                missing_locations = EXCLUDED.missing_locations,
                failure_code = EXCLUDED.failure_code,
                version = EXCLUDED.version,
                updated_at = EXCLUDED.updated_at,
                document_type = EXCLUDED.document_type,
                original_filename = EXCLUDED.original_filename
            """;

    private final DataSource dataSource;

    public PostgresRulebookRegistrationRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
    }

    @Override
    public Optional<StoredRulebookRegistration> findById(RulebookId id) {
        return queryOne(FIND_BY_ID, id.value());
    }

    @Override
    public Optional<StoredRulebookRegistration> findByOperationKey(String operationKey) {
        String normalized = Objects.requireNonNull(operationKey, "operationKey must not be null").trim();
        return queryOne(FIND_BY_OPERATION_KEY, new String[] {normalized, "%|" + escapeLike(normalized) + "|%"});
    }

    @Override
    public Optional<StoredRulebookRegistration> findByOwnerAndContentHash(OwnerPlayerId owner, String contentHash) {
        Objects.requireNonNull(owner, "owner must not be null");
        Objects.requireNonNull(contentHash, "contentHash must not be null");
        try (Connection connection = dataSource.getConnection();
                PreparedStatement ps = connection.prepareStatement(FIND_BY_OWNER_AND_CONTENT_HASH)) {
            ps.setObject(1, owner.value(), Types.OTHER);
            ps.setString(2, contentHash);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("failed to query rulebook registration by owner and content hash", e);
        }
    }

    @Override
    public List<StoredRulebookRegistration> findByOwner(OwnerPlayerId owner) {
        Objects.requireNonNull(owner, "owner must not be null");
        try (Connection connection = dataSource.getConnection();
                PreparedStatement ps = connection.prepareStatement(FIND_BY_OWNER)) {
            ps.setObject(1, owner.value(), Types.OTHER);
            try (ResultSet rs = ps.executeQuery()) {
                List<StoredRulebookRegistration> results = new ArrayList<>();
                while (rs.next()) {
                    results.add(mapRow(rs));
                }
                return List.copyOf(results);
            }
        } catch (SQLException e) {
            throw new RuntimeException("failed to query rulebook registrations", e);
        }
    }

    @Override
    public List<StoredRulebookRegistration> findByProcessingStatuses(List<ProcessingStatus> statuses) {
        List<ProcessingStatus> requested = List.copyOf(Objects.requireNonNull(statuses, "statuses must not be null"));
        if (requested.isEmpty()) {
            return List.of();
        }
        String sql = FIND_BY_STATUS_PREFIX + requested.stream().map(status -> "?").reduce((left, right) -> left + ", " + right).orElse("?") + ") ORDER BY created_at ASC";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)) {
            for (int index = 0; index < requested.size(); index++) {
                ps.setString(index + 1, requested.get(index).name());
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<StoredRulebookRegistration> results = new ArrayList<>();
                while (rs.next()) {
                    results.add(mapRow(rs));
                }
                return List.copyOf(results);
            }
        } catch (SQLException e) {
            throw new RuntimeException("failed to query rulebook registrations by status", e);
        }
    }

    @Override
    public List<StoredRulebookRegistration> claimPending(Instant processingLeaseCutoff, int limit) {
        Objects.requireNonNull(processingLeaseCutoff, "processingLeaseCutoff must not be null");
        if (limit <= 0) {
            return List.of();
        }
        try (Connection connection = dataSource.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement ps = connection.prepareStatement(CLAIM_PENDING)) {
                ps.setTimestamp(1, Timestamp.from(processingLeaseCutoff));
                ps.setInt(2, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    List<StoredRulebookRegistration> claimed = new ArrayList<>();
                    while (rs.next()) {
                        claimed.add(mapRow(rs));
                    }
                    connection.commit();
                    return List.copyOf(claimed);
                }
            } catch (SQLException e) {
                try {
                    connection.rollback();
                } catch (SQLException rollback) {
                    e.addSuppressed(rollback);
                }
                throw new RuntimeException("failed to claim pending rulebook registrations", e);
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException e) {
            throw new RuntimeException("failed to access rulebook registration claim transaction", e);
        }
    }

    @Override
    public void save(StoredRulebookRegistration registration) {
        Objects.requireNonNull(registration, "registration must not be null");
        try (Connection connection = dataSource.getConnection();
                PreparedStatement ps = connection.prepareStatement(UPSERT)) {
            ps.setObject(1, registration.rulebookId().value(), Types.OTHER);
            ps.setObject(2, registration.ownerPlayerId().value(), Types.OTHER);
            ps.setString(3, registration.operationKey());
            ps.setString(4, registration.contentHash());
            ps.setString(5, registration.format().name());
            ps.setLong(6, registration.fileSize());
            ps.setString(7, registration.storageKey());
            ps.setString(8, registration.processingStatus().name());
            setNullableString(ps, 9, registration.extractionStatus() != null ? registration.extractionStatus().name() : null);
            setNullableString(ps, 10, registration.extractedContent());
            setStringArray(ps, 11, registration.missingLocations());
            setNullableString(ps, 12, registration.failureCode());
            ps.setLong(13, registration.version());
            ps.setTimestamp(14, Timestamp.from(registration.createdAt()));
            ps.setTimestamp(15, Timestamp.from(registration.updatedAt()));
            ps.setString(16, registration.documentType().name());
            ps.setString(17, registration.originalFilename());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("failed to save rulebook registration", e);
        }
    }

    private Optional<StoredRulebookRegistration> queryOne(String sql, Object param) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)) {
            if (param instanceof UUID uuid) {
                ps.setObject(1, uuid, Types.OTHER);
            } else if (param instanceof String[] values) {
                ps.setString(1, values[0]);
                ps.setString(2, values[1]);
            } else if (param instanceof String str) {
                ps.setString(1, str);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("failed to query rulebook registration", e);
        }
    }

    private static StoredRulebookRegistration mapRow(ResultSet rs) throws SQLException {
        return new StoredRulebookRegistration(
                new RulebookId(rs.getObject("rulebook_id", UUID.class)),
                new OwnerPlayerId(rs.getObject("owner_player_id", UUID.class)),
                rs.getString("operation_key"),
                rs.getString("content_hash"),
                RulebookFormat.valueOf(rs.getString("format")),
                rs.getLong("file_size"),
                rs.getString("storage_key"),
                ProcessingStatus.valueOf(rs.getString("processing_status")),
                getNullableEnum(rs, "extraction_status", ExtractionStatus.class),
                rs.getString("extracted_content"),
                getStringArray(rs, "missing_locations"),
                rs.getString("failure_code"),
                rs.getLong("version"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant(),
                DocumentType.valueOf(rs.getString("document_type")),
                rs.getString("original_filename"));
    }

    private static <E extends Enum<E>> E getNullableEnum(ResultSet rs, String column, Class<E> type) throws SQLException {
        String value = rs.getString(column);
        return value != null ? Enum.valueOf(type, value) : null;
    }

    private static List<String> getStringArray(ResultSet rs, String column) throws SQLException {
        Array array = rs.getArray(column);
        if (array == null) return List.of();
        String[] values = (String[]) array.getArray();
        return List.of(values);
    }

    private static void setNullableString(PreparedStatement ps, int index, String value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.VARCHAR);
        } else {
            ps.setString(index, value);
        }
    }

    private static void setStringArray(PreparedStatement ps, int index, List<String> values) throws SQLException {
        if (values == null || values.isEmpty()) {
            ps.setNull(index, Types.ARRAY);
        } else {
            ps.setArray(index, ps.getConnection().createArrayOf("text", values.toArray(new String[0])));
        }
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
