package com.dndmaster.adventure.domain.runtime;

import java.util.Objects;
import java.util.UUID;

/** A fact established during this playthrough when source lookup found no answer. */
public record RuntimeAddedFact(UUID factId, String content, UUID establishedTurnId, String subject) {
    public RuntimeAddedFact(UUID factId, String content, UUID establishedTurnId) {
        this(factId, content, establishedTurnId, inferSubject(content));
    }

    public RuntimeAddedFact {
        Objects.requireNonNull(factId, "runtime fact id must not be null");
        Objects.requireNonNull(establishedTurnId, "established turn id must not be null");
        if (content == null || content.isBlank()) throw new IllegalArgumentException("runtime fact content must not be blank");
        content = content.trim();
        subject = subject == null ? "" : subject.trim();
    }

    private static String inferSubject(String content) {
        if (content == null) return "";
        String normalized = content.toLowerCase(java.util.Locale.ROOT);
        if (normalized.matches(".*(reward|보상|대가|gold|gp|골드|금화).*")) return "reward";
        if (normalized.matches(".*(price|값|금액|가격).*")) return "price";
        if (normalized.matches(".*(name|이름).*")) return "name";
        return "";
    }
}
