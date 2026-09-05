package com.dndmaster.adventure.domain.combat;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Player-facing terminal projection: summary only, never a combat replay. */
public final class PostCombatProjectionPolicy {
    private PostCombatProjectionPolicy() {}

    public static PostCombatSummary summary(CombatEndProposal proposal, List<CombatEvent> events) {
        Objects.requireNonNull(proposal, "end proposal must not be null");
        Objects.requireNonNull(events, "combat events must not be null");
        return new PostCombatSummary(proposal.adventureId(), proposal.encounterId(), proposal.reasonCode(),
                proposal.summary(), false, List.of());
    }

    public record PostCombatSummary(java.util.UUID adventureId, java.util.UUID encounterId, String reason,
                                    String summary, boolean detailedReplayAvailable, List<CombatEvent> replay) {
        public PostCombatSummary {
            replay = List.of();
            detailedReplayAvailable = false;
        }
    }

    public static CombatEvent endedEvent(CombatEndProposal proposal, long sequence) {
        String summary = proposal.summary().replace("\\", "\\\\").replace("\"", "\\\"");
        return new CombatEvent(proposal.encounterId(), sequence, "COMBAT_ENDED",
                "{\"adventureId\":\"" + proposal.adventureId() + "\",\"reason\":\""
                        + proposal.reasonCode() + "\",\"summary\":\"" + summary + "\"}");
    }

    public static PostCombatSummary fromEndedEvent(CombatEvent event) {
        Objects.requireNonNull(event, "combat ended event must not be null");
        if (!"COMBAT_ENDED".equals(event.eventType())) throw new IllegalArgumentException("combat ended event required");
        return new PostCombatSummary(UUID.fromString(value(event.playerPayload(), "adventureId")), event.encounterId(),
                value(event.playerPayload(), "reason"), value(event.playerPayload(), "summary"), false, List.of());
    }

    private static String value(String payload, String key) {
        String marker = "\"" + key + "\":\"";
        int start = payload.indexOf(marker);
        if (start < 0) return "";
        start += marker.length();
        StringBuilder result = new StringBuilder();
        boolean escaped = false;
        for (int index = start; index < payload.length(); index++) {
            char current = payload.charAt(index);
            if (escaped) { result.append(current); escaped = false; continue; }
            if (current == '\\') { escaped = true; continue; }
            if (current == '"') break;
            result.append(current);
        }
        return result.toString();
    }
}
