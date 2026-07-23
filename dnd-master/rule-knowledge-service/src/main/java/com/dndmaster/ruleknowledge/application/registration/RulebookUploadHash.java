package com.dndmaster.ruleknowledge.application.registration;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

public final class RulebookUploadHash {
    private RulebookUploadHash() {}

    public static String sha256(byte[] content) {
        Objects.requireNonNull(content, "content must not be null");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }
}
