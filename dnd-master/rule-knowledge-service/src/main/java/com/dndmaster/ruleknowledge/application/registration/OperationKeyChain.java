package com.dndmaster.ruleknowledge.application.registration;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class OperationKeyChain {
    private static final String DELIMITER = "|";

    private OperationKeyChain() {}

    public static String canonicalize(String operationKey) {
        String normalized = normalize(operationKey);
        return containsDelimiter(normalized) ? normalized : wrap(normalized);
    }

    public static String append(String existingOperationKey, String replayOperationKey) {
        String normalizedExisting = canonicalize(existingOperationKey);
        String normalizedReplay = normalize(replayOperationKey);
        if (contains(normalizedExisting, normalizedReplay)) {
            return normalizedExisting;
        }
        return normalizedExisting + normalizedReplay + DELIMITER;
    }

    public static boolean contains(String storedOperationKey, String queryOperationKey) {
        String normalizedStored = normalize(storedOperationKey);
        String normalizedQuery = normalize(queryOperationKey);
        if (normalizedStored.equals(normalizedQuery)) {
            return true;
        }
        return normalizedStored.contains(wrap(normalizedQuery));
    }

    public static List<String> keys(String storedOperationKey) {
        String normalized = canonicalize(storedOperationKey);
        if (!containsDelimiter(normalized)) {
            return List.of(normalized);
        }
        String body = normalized.substring(1, normalized.length() - 1);
        if (body.isBlank()) {
            return List.of();
        }
        String[] parts = body.split("\\|");
        List<String> keys = new ArrayList<>(parts.length);
        for (String part : parts) {
            if (!part.isBlank()) {
                keys.add(part);
            }
        }
        return List.copyOf(keys);
    }

    private static String normalize(String operationKey) {
        Objects.requireNonNull(operationKey, "operationKey must not be null");
        return operationKey.trim();
    }

    private static boolean containsDelimiter(String value) {
        return value.startsWith(DELIMITER) && value.endsWith(DELIMITER) && value.length() >= 2;
    }

    private static String wrap(String value) {
        return DELIMITER + value + DELIMITER;
    }
}
