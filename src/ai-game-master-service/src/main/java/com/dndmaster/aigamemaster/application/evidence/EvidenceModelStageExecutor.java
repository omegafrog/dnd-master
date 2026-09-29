package com.dndmaster.aigamemaster.application.evidence;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.function.Function;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class EvidenceModelStageExecutor {
    private static final Logger log = LoggerFactory.getLogger(EvidenceModelStageExecutor.class);
    private final EvidenceModelPort model;

    EvidenceModelStageExecutor(EvidenceModelPort model) { this.model = model; }

    <T> T execute(UUID soloPlayerId, String operationId, String instruction, Function<String, T> parser) {
        String requestId = operationId + ":" + fingerprint(instruction);
        String raw = model.complete(soloPlayerId, requestId, instruction);
        try {
            return parser.apply(raw);
        } catch (EvidenceModelOutputException exception) {
            log.warn("evidence model output rejected requestId={} category={} causeType={}", requestId,
                    exception.category().name(), causeType(exception));
            throw exception;
        }
    }

    private static String causeType(Throwable failure) {
        return failure.getCause() == null ? "NONE" : failure.getCause().getClass().getSimpleName();
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
