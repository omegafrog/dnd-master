package com.dndmaster.character;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dndmaster.character.application.*;
import com.dndmaster.character.domain.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class CharacterSheetsDeletionConsumerTest {
    @Test
    void repeated_event_deletes_each_sheet_once_safely() {
        Repository repository = new Repository();
        CharacterSheet sheet = new CharacterSheet(CharacterSheetId.generate(), new AdventureId(UUID.randomUUID()), SheetEdition.DND_5E_2024, new CharacterSheetData2024("Aria", 1, false));
        repository.values.put(sheet.id(), sheet);
        CharacterSheetsDeletionConsumer consumer = new CharacterSheetsDeletionConsumer(repository);
        CharacterSheetsDeletionRequested event = new CharacterSheetsDeletionRequested(UUID.randomUUID(), List.of(sheet.id().value(), sheet.id().value()));

        consumer.consume(event);
        consumer.consume(event);

        assertEquals(1, repository.deletes);
        assertEquals(0, repository.values.size());
    }

    private static final class Repository implements CharacterSheetRepository {
        final Map<CharacterSheetId, CharacterSheet> values = new HashMap<>(); int deletes;
        public Optional<CharacterSheet> findById(CharacterSheetId id) { return Optional.ofNullable(values.get(id)); }
        public Optional<CharacterSheet> findByCommandId(UUID id) { return Optional.empty(); }
        public void save(CharacterSheet sheet) { values.put(sheet.id(), sheet); }
        public void save(CharacterSheet sheet, long version, UUID key, String fingerprint) { values.put(sheet.id(), sheet); }
        public void deleteById(CharacterSheetId id) { if (values.remove(id) != null) deletes++; }
    }
}
