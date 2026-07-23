package com.dndmaster.ruleknowledge.application.registration;

import com.dndmaster.ruleknowledge.domain.rulebook.FileSize;
import com.dndmaster.ruleknowledge.domain.rulebook.OwnerPlayerId;
import com.dndmaster.ruleknowledge.domain.rulebook.Rulebook;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookFormat;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookId;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class RulebookRegistrationApplicationService {
    private final RulebookFileStorage fileStorage;
    private final RulebookContentExtractor contentExtractor;
    private final ConcurrentMap<String, UploadResult> completedUploadsByOperationKey = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, UploadResult> completedUploadsByOwnerAndHash = new ConcurrentHashMap<>();

    public RulebookRegistrationApplicationService(
            RulebookFileStorage fileStorage, RulebookContentExtractor contentExtractor) {
        this.fileStorage = Objects.requireNonNull(fileStorage, "fileStorage must not be null");
        this.contentExtractor = Objects.requireNonNull(contentExtractor, "contentExtractor must not be null");
    }

    public RegisteredRulebook uploadRulebook(
            OwnerPlayerId ownerPlayerId, RulebookFormat format, byte[] fileContent) {
        return uploadRulebook(UUID.randomUUID().toString(), ownerPlayerId, format, fileContent);
    }

    public RegisteredRulebook uploadRulebook(
            String operationKey, OwnerPlayerId ownerPlayerId, RulebookFormat format, byte[] fileContent) {
        if (operationKey == null || operationKey.isBlank()) {
            throw new IllegalArgumentException("operation key must not be blank");
        }
        Objects.requireNonNull(fileContent, "fileContent must not be null");
        byte[] safeContent = Arrays.copyOf(fileContent, fileContent.length);
        String fingerprint = RulebookUploadHash.sha256(safeContent);
        String operationKeyValue = operationKey.trim();
        UploadResult existingByOperationKey = completedUploadsByOperationKey.get(operationKeyValue);
        if (existingByOperationKey != null) {
            if (!existingByOperationKey.ownerPlayerId().equals(ownerPlayerId)
                    || !existingByOperationKey.fingerprint().equals(fingerprint)) {
                throw new RulebookUploadConflictException();
            }
            return existingByOperationKey.registration();
        }
        String ownerHashKey = ownerHashKey(ownerPlayerId, fingerprint);
        UploadResult existingByOwnerAndHash = completedUploadsByOwnerAndHash.get(ownerHashKey);
        if (existingByOwnerAndHash != null) {
            completedUploadsByOperationKey.putIfAbsent(operationKeyValue, existingByOwnerAndHash);
            return existingByOwnerAndHash.registration();
        }
        RulebookId rulebookId = RulebookId.generate();
        Rulebook rulebook = Rulebook.acceptUpload(
                rulebookId, ownerPlayerId, format, new FileSize(safeContent.length));
        StoredRulebookFile storedFile = fileStorage.store(rulebookId, safeContent);
        UploadResult created = new UploadResult(ownerPlayerId, fingerprint, new RegisteredRulebook(rulebook, storedFile));
        completedUploadsByOwnerAndHash.put(ownerHashKey, created);
        completedUploadsByOperationKey.put(operationKeyValue, created);
        return created.registration();
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

    private static String ownerHashKey(OwnerPlayerId ownerPlayerId, String fingerprint) {
        Objects.requireNonNull(ownerPlayerId, "ownerPlayerId must not be null");
        Objects.requireNonNull(fingerprint, "fingerprint must not be null");
        return ownerPlayerId.value() + ":" + fingerprint;
    }

    private record UploadResult(OwnerPlayerId ownerPlayerId, String fingerprint, RegisteredRulebook registration) {}
}
