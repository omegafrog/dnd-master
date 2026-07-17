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
    private final DataSource dataSource;
    private final Map<CharacterSheetId, Long> loadedVersions = new ConcurrentHashMap<>();

    public PostgresCharacterSheetRepository(DataSource dataSource) {
        this.dataSource = java.util.Objects.requireNonNull(dataSource, "data source must not be null");
    }

    @Override
    public Optional<CharacterSheet> findById(CharacterSheetId id) {
        return findVersionedById(id).map(VersionedCharacterSheet::sheet);
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
        if (expectedVersion == null) insert(sheet);
        else update(sheet, expectedVersion);
    }

    public long update(CharacterSheet sheet, long expectedVersion) {
        String sql = "UPDATE " + TABLE + " SET edition=?, character_name=?, character_level=?, inspiration=?, version=version+1 "
                + "WHERE character_sheet_id=? AND version=?";
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            bindData(statement, sheet, 1);
            statement.setObject(5, sheet.id().value());
            statement.setLong(6, expectedVersion);
            if (statement.executeUpdate() != 1) throw new OptimisticCharacterSheetLockException();
            long newVersion = expectedVersion + 1;
            loadedVersions.put(sheet.id(), newVersion);
            return newVersion;
        } catch (OptimisticCharacterSheetLockException exception) {
            throw exception;
        } catch (SQLException exception) {
            throw failure("could not update character sheet", exception);
        }
    }

    private void insert(CharacterSheet sheet) {
        String sql = "INSERT INTO " + TABLE
                + " (character_sheet_id, adventure_id, edition, character_name, character_level, inspiration, version)"
                + " VALUES (?, ?, ?, ?, ?, ?, 0)";
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, sheet.id().value());
            statement.setObject(2, sheet.adventureId().value());
            bindData(statement, sheet, 3);
            statement.executeUpdate();
            loadedVersions.put(sheet.id(), 0L);
        } catch (SQLException exception) {
            throw failure("could not insert character sheet", exception);
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
                new AdventureId(row.getObject("adventure_id", UUID.class)), edition, data);
        return new VersionedCharacterSheet(sheet, row.getLong("version"));
    }

    private static CharacterSheetPersistenceException failure(String message, Throwable cause) {
        return new CharacterSheetPersistenceException(message, cause);
    }
}
