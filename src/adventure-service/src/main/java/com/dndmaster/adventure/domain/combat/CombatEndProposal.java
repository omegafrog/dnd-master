package com.dndmaster.adventure.domain.combat;

import java.util.Objects;
import java.util.UUID;

/** Typed, provider-independent proposal for ending an encounter. */
public record CombatEndProposal(UUID adventureId, UUID encounterId, Source source, Reason reason,
                                String summary, boolean accepted) {
    public enum Source { GM, PLAYER, SYSTEM }
    public enum Reason {
        ENEMIES_DEFEATED, SURRENDER, FLED, ESCAPED, NEGOTIATED, OBJECTIVE_ACHIEVED, OTHER
    }

    public CombatEndProposal {
        Objects.requireNonNull(adventureId, "adventure id must not be null");
        Objects.requireNonNull(encounterId, "encounter id must not be null");
        Objects.requireNonNull(source, "proposal source must not be null");
        Objects.requireNonNull(reason, "end reason must not be null");
        if (summary == null || summary.isBlank()) throw new IllegalArgumentException("end summary must not be blank");
        summary = summary.trim();
        if (summary.length() > 4096) throw new IllegalArgumentException("end summary is too long");
    }

    public CombatEndProposal(UUID adventureId, UUID encounterId, Reason reason, String summary) {
        this(adventureId, encounterId, Source.GM, reason, summary, true);
    }

    public CombatEndProposal(boolean accepted, UUID adventureId, UUID encounterId, String reason, String summary) {
        this(adventureId, encounterId, Source.GM, parseReason(reason), summary, accepted);
    }

    public CombatEndProposal(UUID adventureId, UUID encounterId, Source source, Reason reason, String summary) {
        this(adventureId, encounterId, source, reason, summary, true);
    }

    public static CombatEndProposal gm(UUID adventureId, UUID encounterId, Reason reason, String summary) {
        return new CombatEndProposal(adventureId, encounterId, Source.GM, reason, summary, true);
    }

    public static CombatEndProposal systemEnemiesDefeated(UUID adventureId, UUID encounterId) {
        return new CombatEndProposal(adventureId, encounterId, Source.SYSTEM, Reason.ENEMIES_DEFEATED,
                "모든 적이 쓰러져 전투가 끝났습니다.", true);
    }

    public String reasonCode() { return reason.name(); }

    private static Reason parseReason(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("end reason must not be blank");
        try { return Reason.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT)); }
        catch (IllegalArgumentException exception) { throw new IllegalArgumentException("unsupported combat end reason", exception); }
    }
}
