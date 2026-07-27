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

    @Override
    public void deleteById(CharacterSheetId id) {
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement("DELETE FROM " + TABLE + " WHERE character_sheet_id = ?")) {
            statement.setObject(1, id.value());
            statement.executeUpdate();
            loadedVersions.remove(id);
        } catch (SQLException exception) {
            throw failure("could not delete character sheet", exception);
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
        String sql = "UPDATE " + TABLE + " SET edition=?, character_name=?, character_level=?, inspiration=?, race=?, character_class=?, background=?, starting_abilities=?, operation_key=?, operation_fingerprint=?, version=version+1, updated_at=CURRENT_TIMESTAMP "
                + "WHERE character_sheet_id=? AND version=?";
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                bindData(statement, sheet, 1);
                statement.setObject(9, operationKey);
                statement.setString(10, operationFingerprint);
                statement.setObject(11, sheet.id().value());
                statement.setLong(12, expectedVersion);
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
                + " (character_sheet_id, adventure_id, session_id, edition, character_name, character_level, inspiration, race, character_class, background, starting_abilities, operation_key, operation_fingerprint, version)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setObject(1, sheet.id().value());
                statement.setObject(2, sheet.adventureId().value());
                statement.setObject(3, sheet.sessionId().value());
                bindData(statement, sheet, 4);
                statement.setObject(12, operationKey);
                statement.setString(13, operationFingerprint);
                statement.setLong(14, persistedVersion);
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
                + " (command_id, character_sheet_id, adventure_id, edition, character_name, character_level, inspiration, race, character_class, background, starting_abilities, operation_key, operation_fingerprint, version)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                + " ON CONFLICT (command_id) DO UPDATE SET"
                + " character_sheet_id = EXCLUDED.character_sheet_id,"
                + " adventure_id = EXCLUDED.adventure_id,"
                + " edition = EXCLUDED.edition,"
                + " character_name = EXCLUDED.character_name,"
                + " character_level = EXCLUDED.character_level,"
                + " inspiration = EXCLUDED.inspiration, race = EXCLUDED.race, character_class = EXCLUDED.character_class, background = EXCLUDED.background, starting_abilities = EXCLUDED.starting_abilities,"
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
            statement.setString(8, sheet.data().race());
            statement.setString(9, sheet.data().characterClass());
            statement.setString(10, sheet.data().background());
            statement.setString(11, sheet.data().startingAbilities());
            statement.setObject(12, operationKey);
            statement.setString(13, operationFingerprint);
            statement.setLong(14, sheet.version());
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
        statement.setString(offset + 4, sheet.data().race());
        statement.setString(offset + 5, sheet.data().characterClass());
        statement.setString(offset + 6, sheet.data().background());
        statement.setString(offset + 7, sheet.data().startingAbilities());
    }

    private static VersionedCharacterSheet map(ResultSet row) throws SQLException {
        SheetEdition edition = SheetEdition.valueOf(row.getString("edition"));
        String name = row.getString("character_name");
        int level = row.getInt("character_level");
        boolean inspiration = row.getBoolean("inspiration");
        String race = row.getString("race");
        String characterClass = row.getString("character_class");
        String background = row.getString("background");
        String startingAbilities = row.getString("starting_abilities");
        CharacterSheetData data = switch (edition) {
            case DND_5E_2014 -> new CharacterSheetData2014(name, level, inspiration, race, characterClass, background, startingAbilities);
            case DND_5E_2024 -> new CharacterSheetData2024(name, level, inspiration, race, characterClass, background, startingAbilities);
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
