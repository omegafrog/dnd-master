package com.dndmaster.character;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.character.application.CharacterSheetApplicationService;
import com.dndmaster.character.application.CreateCharacterSheetCommand;
import com.dndmaster.character.application.SessionCharacterPolicy;
import com.dndmaster.character.domain.*;
import java.util.UUID;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SessionScopedCharacterCreationTest {
    @Test
    void stores_session_id_as_character_sheet_ownership_key() {
        var repository = new InMemoryRepository();
        var sessionId = new SessionId(UUID.randomUUID());
        var service = new CharacterSheetApplicationService(
                repository, id -> SheetEdition.DND_5E_2024,
                id -> SessionCharacterPolicy.draft());

        var sheet = service.createSheet(new CreateCharacterSheetCommand(
                sessionId, SheetEdition.DND_5E_2024,
                new CharacterSheetData2024("Aria", 1, false)));

        assertEquals(sessionId, sheet.sessionId());
    }

    @Test
    void stores_player_owner_for_cross_context_validation() {
        var owner = UUID.randomUUID();
        var service = new CharacterSheetApplicationService(new InMemoryRepository(), id -> SheetEdition.DND_5E_2024,
                id -> SessionCharacterPolicy.draft());
        var sheet = service.createSheet(new CreateCharacterSheetCommand(new SessionId(UUID.randomUUID()), owner,
                SheetEdition.DND_5E_2024, new CharacterSheetData2024("Aria", 1, false)));
        assertEquals(owner, sheet.ownerPlayerId());
    }

    @Test
    void rejects_creation_without_session_id() {
        assertThrows(NullPointerException.class, () ->
                new CreateCharacterSheetCommand((SessionId) null, SheetEdition.DND_5E_2024,
                        new CharacterSheetData2024("Aria", 1, false)));
    }

    private static final class InMemoryRepository implements com.dndmaster.character.application.CharacterSheetRepository {
        private CharacterSheet sheet;
        public Optional<CharacterSheet> findById(CharacterSheetId id) { return Optional.ofNullable(sheet); }
        public Optional<CharacterSheet> findByCommandId(UUID id) { return Optional.empty(); }
        public void save(CharacterSheet value) { sheet = value; }
        public void save(CharacterSheet value, long version, UUID key, String fingerprint) { sheet = value; }
        public void deleteById(CharacterSheetId id) { sheet = null; }
    }
}
