package com.dndmaster.adventure.application.runtime;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

/** Compatibility name for the bounded verification policy and same-turn identity. */
public final class NarrativeVerificationPolicy extends VerificationPolicy {
    public String fingerprint(String turnId, String planId, List<String> outcomes) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest((turnId + "|" + planId + "|" + outcomes).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException("could not fingerprint resolved turn", e); }
    }
}
