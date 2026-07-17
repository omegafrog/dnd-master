package com.dndmaster.ruleknowledge.application.registration;

import com.dndmaster.ruleknowledge.domain.rulebook.FileSize;
import com.dndmaster.ruleknowledge.domain.rulebook.OwnerPlayerId;
import com.dndmaster.ruleknowledge.domain.rulebook.Rulebook;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookFormat;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookId;
import java.util.Arrays;
import java.util.Objects;

public final class RulebookRegistrationApplicationService {
    private final RulebookFileStorage fileStorage;
    private final RulebookContentExtractor contentExtractor;

    public RulebookRegistrationApplicationService(
            RulebookFileStorage fileStorage, RulebookContentExtractor contentExtractor) {
        this.fileStorage = Objects.requireNonNull(fileStorage, "fileStorage must not be null");
        this.contentExtractor = Objects.requireNonNull(contentExtractor, "contentExtractor must not be null");
    }

    public RegisteredRulebook uploadRulebook(
            OwnerPlayerId ownerPlayerId, RulebookFormat format, byte[] fileContent) {
        Objects.requireNonNull(fileContent, "fileContent must not be null");
        byte[] safeContent = Arrays.copyOf(fileContent, fileContent.length);
        RulebookId rulebookId = RulebookId.generate();
        Rulebook rulebook = Rulebook.acceptUpload(
                rulebookId, ownerPlayerId, format, new FileSize(safeContent.length));
        StoredRulebookFile storedFile = fileStorage.store(rulebookId, safeContent);
        return new RegisteredRulebook(rulebook, storedFile);
    }

    public void extractContent(RegisteredRulebook registration) {
        Objects.requireNonNull(registration, "registration must not be null");
        byte[] storedContent = fileStorage.read(registration.storedFile());
        registration.rulebook().recordExtraction(
                contentExtractor.extract(registration.rulebook().format(), Arrays.copyOf(storedContent, storedContent.length)));
    }

    public void confirmPartialExtraction(RegisteredRulebook registration) {
        Objects.requireNonNull(registration, "registration must not be null");
        registration.rulebook().confirmPartialExtraction();
    }
}
