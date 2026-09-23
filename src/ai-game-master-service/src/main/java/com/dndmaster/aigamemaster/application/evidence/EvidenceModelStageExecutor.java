package com.dndmaster.aigamemaster.application.evidence;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.function.Function;
import java.util.UUID;

final class EvidenceModelStageExecutor {
    private final EvidenceModelPort model;

    EvidenceModelStageExecutor(EvidenceModelPort model) { this.model = model; }

    <T> T execute(UUID soloPlayerId, String operationId, String instruction, Function<String, T> parser) {
        return parser.apply(model.complete(soloPlayerId, operationId + ":" + fingerprint(instruction), instruction));
    }

    private static String fingerprint(String instruction) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(instruction.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
