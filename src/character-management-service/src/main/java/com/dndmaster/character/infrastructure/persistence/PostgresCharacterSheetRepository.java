package com.dndmaster.character.infrastructure.persistence;

import com.dndmaster.character.application.CharacterSheetRepository;
import com.dndmaster.character.domain.*;
import java.sql.*;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;

public final class PostgresCharacterSheetRepository implements CharacterSheetRepository {
    private static final String TABLE = "character_management.character_sheet";
    private static final String HISTORY_TABLE = "character_management.character_sheet_command_history";
    private final DataSource dataSource;
    private final Map<CharacterSheetId, Long> loadedVersions = new ConcurrentHashMap<>();

    public PostgresCharacterSheetRepository(DataSource dataSource) {
        this.dataSource = java.util.Objects.requireNonNull(dataSource, "data source must not be null");
    }

    @Override
    public Optional<CharacterSheet> findById(CharacterSheetId id) {
        return findVersionedById(id).map(VersionedCharacterSheet::sheet);
    }

    @Override
    public Optional<CharacterSheet> findByCommandId(UUID commandId) {
        String sql = "SELECT * FROM " + HISTORY_TABLE + " WHERE command_id = ?";
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, commandId);
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? Optional.of(map(row).sheet()) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw failure("could not load character sheet by command id", exception);
        }
    }

    public Optional<VersionedCharacterSheet> findVersionedById(CharacterSheetId id) {
        String sql = "SELECT * FROM " + TABLE + " WHERE character_sheet_id = ?";
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, id.value());
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) return Optional.empty();
                VersionedCharacterSheet result = map(row);
                loadedVersions.put(id, result.version());
                return Optional.of(result);
            }
        } catch (SQLException exception) {
            throw failure("could not load character sheet", exception);
        }
    }

    @Override
    public void save(CharacterSheet sheet) {
        Long expectedVersion = loadedVersions.get(sheet.id());
        if (expectedVersion == null) insert(sheet, sheet.version(), sheet.operationKey(), sheet.operationFingerprint());
        else update(sheet, expectedVersion);
    }

    @Override
    public void save(CharacterSheet sheet, long persistedVersion, UUID operationKey, String operationFingerprint) {
        Long expectedVersion = loadedVersions.get(sheet.id());
        if (expectedVersion == null) {
            insert(sheet, persistedVersion, operationKey, operationFingerprint);
            return;
        }
        update(sheet, expectedVersion, persistedVersion, operationKey, operationFingerprint);
    }

    public long update(CharacterSheet sheet, long expectedVersion) {
        return update(sheet, expectedVersion, sheet.version(), sheet.operationKey(), sheet.operationFingerprint());
    }

    public long update(
            CharacterSheet sheet,
            long expectedVersion,
            long persistedVersion,
            UUID operationKey,
            String operationFingerprint) {
        if (persistedVersion != expectedVersion + 1) {
            throw new IllegalArgumentException("persisted version must advance by one");
        }
        String sql = "UPDATE " + TABLE + " SET edition=?, character_name=?, character_level=?, inspiration=?, operation_key=?, operation_fingerprint=?, version=version+1, updated_at=CURRENT_TIMESTAMP "
                + "WHERE character_sheet_id=? AND version=?";
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                bindData(statement, sheet, 1);
                statement.setObject(5, operationKey);
                statement.setString(6, operationFingerprint);
                statement.setObject(7, sheet.id().value());
                statement.setLong(8, expectedVersion);
                if (statement.executeUpdate() != 1) throw new OptimisticCharacterSheetLockException();
                long newVersion = persistedVersion;
                sheet.markPersisted(newVersion, operationKey, operationFingerprint);
                recordHistory(connection, sheet, operationKey, operationFingerprint);
                connection.commit();
                loadedVersions.put(sheet.id(), newVersion);
                return newVersion;
            } catch (OptimisticCharacterSheetLockException exception) {
                connection.rollback();
                throw exception;
            } catch (SQLException exception) {
                connection.rollback();
                throw failure("could not update character sheet", exception);
            }
        } catch (SQLException exception) {
            throw failure("could not update character sheet", exception);
        }
    }

    private void insert(CharacterSheet sheet, long persistedVersion, UUID operationKey, String operationFingerprint) {
        String sql = "INSERT INTO " + TABLE
                + " (character_sheet_id, adventure_id, edition, character_name, character_level, inspiration, operation_key, operation_fingerprint, version)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setObject(1, sheet.id().value());
                statement.setObject(2, sheet.adventureId().value());
                bindData(statement, sheet, 3);
                statement.setObject(7, operationKey);
                statement.setString(8, operationFingerprint);
                statement.setLong(9, persistedVersion);
                statement.executeUpdate();
                sheet.markPersisted(persistedVersion, operationKey, operationFingerprint);
                recordHistory(connection, sheet, operationKey, operationFingerprint);
                connection.commit();
                loadedVersions.put(sheet.id(), persistedVersion);
            } catch (SQLException exception) {
                connection.rollback();
                throw failure("could not insert character sheet", exception);
            }
        } catch (SQLException exception) {
            throw failure("could not insert character sheet", exception);
        }
    }

    private void recordHistory(Connection connection, CharacterSheet sheet, UUID operationKey, String operationFingerprint)
            throws SQLException {
        if (operationKey == null) {
            return;
        }
        String sql = "INSERT INTO " + HISTORY_TABLE
                + " (command_id, character_sheet_id, adventure_id, edition, character_name, character_level, inspiration, operation_key, operation_fingerprint, version)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                + " ON CONFLICT (command_id) DO UPDATE SET"
                + " character_sheet_id = EXCLUDED.character_sheet_id,"
                + " adventure_id = EXCLUDED.adventure_id,"
                + " edition = EXCLUDED.edition,"
                + " character_name = EXCLUDED.character_name,"
                + " character_level = EXCLUDED.character_level,"
                + " inspiration = EXCLUDED.inspiration,"
                + " operation_key = EXCLUDED.operation_key,"
                + " operation_fingerprint = EXCLUDED.operation_fingerprint,"
                + " version = EXCLUDED.version";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, operationKey);
            statement.setObject(2, sheet.id().value());
            statement.setObject(3, sheet.adventureId().value());
            statement.setString(4, sheet.edition().name());
            statement.setString(5, sheet.data().characterName());
            statement.setInt(6, sheet.data().level());
            boolean inspiration = switch (sheet.data()) {
                case CharacterSheetData2014 data -> data.inspiration();
                case CharacterSheetData2024 data -> data.heroicInspiration();
            };
            statement.setBoolean(7, inspiration);
            statement.setObject(8, operationKey);
            statement.setString(9, operationFingerprint);
            statement.setLong(10, sheet.version());
            statement.executeUpdate();
        }
    }

    private static void bindData(PreparedStatement statement, CharacterSheet sheet, int offset) throws SQLException {
        statement.setString(offset, sheet.edition().name());
        statement.setString(offset + 1, sheet.data().characterName());
        statement.setInt(offset + 2, sheet.data().level());
        boolean inspiration = switch (sheet.data()) {
            case CharacterSheetData2014 data -> data.inspiration();
            case CharacterSheetData2024 data -> data.heroicInspiration();
        };
        statement.setBoolean(offset + 3, inspiration);
    }

    private static VersionedCharacterSheet map(ResultSet row) throws SQLException {
        SheetEdition edition = SheetEdition.valueOf(row.getString("edition"));
        String name = row.getString("character_name");
        int level = row.getInt("character_level");
        boolean inspiration = row.getBoolean("inspiration");
        CharacterSheetData data = switch (edition) {
            case DND_5E_2014 -> new CharacterSheetData2014(name, level, inspiration);
            case DND_5E_2024 -> new CharacterSheetData2024(name, level, inspiration);
        };
        CharacterSheet sheet = new CharacterSheet(
                new CharacterSheetId(row.getObject("character_sheet_id", UUID.class)),
                new AdventureId(row.getObject("adventure_id", UUID.class)), edition, data,
                row.getLong("version"),
                row.getString("operation_key") == null ? null : UUID.fromString(row.getString("operation_key")),
                row.getString("operation_fingerprint"));
        return new VersionedCharacterSheet(sheet, row.getLong("version"));
    }

    private static CharacterSheetPersistenceException failure(String message, Throwable cause) {
        return new CharacterSheetPersistenceException(message, cause);
    }
}
