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
                   missing_locations, failure_code, version, created_at, updated_at
              FROM rulebook_registration WHERE rulebook_id = ?
            """;
    private static final String FIND_BY_OPERATION_KEY = """
            SELECT rulebook_id, owner_player_id, operation_key, content_hash, format, file_size,
                   storage_key, processing_status, extraction_status, extracted_content,
                   missing_locations, failure_code, version, created_at, updated_at
              FROM rulebook_registration WHERE operation_key = ?
            """;
    private static final String FIND_BY_OWNER = """
            SELECT rulebook_id, owner_player_id, operation_key, content_hash, format, file_size,
                   storage_key, processing_status, extraction_status, extracted_content,
                   missing_locations, failure_code, version, created_at, updated_at
              FROM rulebook_registration WHERE owner_player_id = ?
              ORDER BY created_at DESC
            """;
    private static final String UPSERT = """
            INSERT INTO rulebook_registration
                (rulebook_id, owner_player_id, operation_key, content_hash, format, file_size,
                 storage_key, processing_status, extraction_status, extracted_content,
                 missing_locations, failure_code, version, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (operation_key) DO UPDATE SET
                owner_player_id = EXCLUDED.owner_player_id,
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
                updated_at = EXCLUDED.updated_at
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
        return queryOne(FIND_BY_OPERATION_KEY, Objects.requireNonNull(operationKey, "operationKey must not be null"));
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
                rs.getTimestamp("updated_at").toInstant());
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
}
