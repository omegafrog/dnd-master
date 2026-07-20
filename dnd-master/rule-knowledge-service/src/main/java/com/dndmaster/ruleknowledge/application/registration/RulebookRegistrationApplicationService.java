package com.dndmaster.ruleknowledge.application.registration;

import com.dndmaster.ruleknowledge.domain.rulebook.FileSize;
import com.dndmaster.ruleknowledge.domain.rulebook.OwnerPlayerId;
import com.dndmaster.ruleknowledge.domain.rulebook.Rulebook;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookFormat;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookId;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class RulebookRegistrationApplicationService {
    private final RulebookFileStorage fileStorage;
    private final RulebookContentExtractor contentExtractor;
    private final ConcurrentMap<String, UploadResult> completedUploads = new ConcurrentHashMap<>();

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
        String fingerprint = fingerprint(ownerPlayerId, format, safeContent);
        return completedUploads.compute(operationKey.trim(), (key, previous) -> {
            if (previous != null) {
                if (!previous.fingerprint().equals(fingerprint)) {
                    throw new RulebookUploadConflictException();
                }
                return previous;
            }
            RulebookId rulebookId = RulebookId.generate();
            Rulebook rulebook = Rulebook.acceptUpload(
                    rulebookId, ownerPlayerId, format, new FileSize(safeContent.length));
            StoredRulebookFile storedFile = fileStorage.store(rulebookId, safeContent);
            return new UploadResult(fingerprint, new RegisteredRulebook(rulebook, storedFile));
        }).registration();
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

    private static String fingerprint(OwnerPlayerId ownerPlayerId, RulebookFormat format, byte[] content) {
        Objects.requireNonNull(ownerPlayerId, "ownerPlayerId must not be null");
        Objects.requireNonNull(format, "format must not be null");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(ownerPlayerId.value().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(format.name().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            digest.update((byte) 0);
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }

    private record UploadResult(String fingerprint, RegisteredRulebook registration) {}
}
