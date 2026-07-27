package com.dndmaster.character.domain;

import java.util.Objects;
import java.util.UUID;

public final class CharacterSheet {
    private final CharacterSheetId id;
    private final AdventureId adventureId;
    private final SessionId sessionId;
    private final SheetEdition edition;
    private CharacterSheetData data;
    private long version;
    private UUID operationKey;
    private String operationFingerprint;

    public CharacterSheet(
            CharacterSheetId id, AdventureId adventureId, SheetEdition edition, CharacterSheetData data) {
        this(id, adventureId, edition, data, 0, null, null);
    }

    public CharacterSheet(
            CharacterSheetId id, AdventureId adventureId, SheetEdition edition, CharacterSheetData data,
            long version, UUID operationKey, String operationFingerprint) {
        this.id = Objects.requireNonNull(id, "character sheet id must not be null");
        this.adventureId = Objects.requireNonNull(adventureId, "adventure id must not be null");
        this.sessionId = new SessionId(adventureId.value());
        this.edition = Objects.requireNonNull(edition, "edition must not be null");
        this.data = requireMatchingData(edition, data);
        if (version < 0) throw new IllegalArgumentException("version must not be negative");
        this.version = version;
        this.operationKey = operationKey;
        this.operationFingerprint = operationFingerprint;
    }

    public CharacterSheet(CharacterSheetId id, SessionId sessionId, SheetEdition edition, CharacterSheetData data) {
        this(id, new AdventureId(sessionId.value()), edition, data, 0, null, null);
    }

    public void authorizeOpen(CharacterSheetOpenRequest request) {
        Objects.requireNonNull(request, "open request must not be null");
        if (!adventureId.equals(request.adventureId())
                || edition != request.appliedEdition()
                || edition != request.requestedEdition()) {
            throw new CharacterSheetEditionMismatchException();
        }
    }

    public void applyUpdate(CharacterSheetUpdate update) {
        Objects.requireNonNull(update, "update must not be null");
        if (update.inputMode() != InputMode.STRUCTURED_SHEET) throw new StructuredSheetRequiredException();
        if (edition != update.edition()) throw new CharacterSheetEditionMismatchException();
        data = requireMatchingData(edition, update.data());
    }

    public void markPersisted(long version, UUID operationKey, String operationFingerprint) {
        if (version < 0) throw new IllegalArgumentException("version must not be negative");
        this.version = version;
        this.operationKey = operationKey;
        this.operationFingerprint = operationFingerprint;
    }

    private static CharacterSheetData requireMatchingData(SheetEdition edition, CharacterSheetData data) {
        Objects.requireNonNull(data, "character sheet data must not be null");
        if (data.edition() != edition) throw new CharacterSheetEditionMismatchException();
        return data;
    }

    public CharacterSheetId id() { return id; }
    public AdventureId adventureId() { return adventureId; }
    public SessionId sessionId() { return sessionId; }
    public SheetEdition edition() { return edition; }
    public CharacterSheetData data() { return data; }
    public long version() { return version; }
    public UUID operationKey() { return operationKey; }
    public String operationFingerprint() { return operationFingerprint; }
}
