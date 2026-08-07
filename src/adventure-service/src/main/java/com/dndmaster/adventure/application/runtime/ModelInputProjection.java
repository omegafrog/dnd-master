package com.dndmaster.adventure.application.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Immutable, fail-closed boundary for data sent to a model provider. */
public record ModelInputProjection(
        List<RuntimeEvidence> storybook,
        List<RuntimeEvidence> rulebook,
        List<RuntimeEvidence> resolution,
        String continuity,
        List<ProjectionAudit> audit) {
    private static final Logger LOG = LoggerFactory.getLogger(ModelInputProjection.class);
    public ModelInputProjection {
        storybook = List.copyOf(Objects.requireNonNull(storybook));
        rulebook = List.copyOf(Objects.requireNonNull(rulebook));
        resolution = List.copyOf(Objects.requireNonNull(resolution));
        continuity = continuity == null ? "" : continuity.trim();
        audit = List.copyOf(Objects.requireNonNull(audit));
    }

    public static ModelInputProjection create(Set<UUID> allowedDocuments,
            List<RuntimeEvidence> storybook, List<RuntimeEvidence> rulebook,
            List<RuntimeEvidence> resolution, String ignoredCheckpoint,
            String continuity, Set<String> committedDisclosureEvents) {
        return create(allowedDocuments, storybook, rulebook, resolution, ignoredCheckpoint, continuity,
                committedDisclosureEvents, Long.MAX_VALUE);
    }

    public static ModelInputProjection create(Set<UUID> allowedDocuments,
            List<RuntimeEvidence> storybook, List<RuntimeEvidence> rulebook,
            List<RuntimeEvidence> resolution, String ignoredCheckpoint,
            String continuity, Set<String> committedDisclosureEvents, long currentTurn) {
        return create(allowedDocuments, Map.of(), storybook, rulebook, resolution, ignoredCheckpoint, continuity,
                committedDisclosureEvents, currentTurn);
    }

    public static ModelInputProjection create(Set<UUID> allowedDocuments, Map<UUID, Long> expectedVersions,
            List<RuntimeEvidence> storybook, List<RuntimeEvidence> rulebook,
            List<RuntimeEvidence> resolution, String ignoredCheckpoint,
            String continuity, Set<String> committedDisclosureEvents, long currentTurn) {
        Objects.requireNonNull(allowedDocuments, "allowed documents must not be null");
        Objects.requireNonNull(expectedVersions, "expected versions must not be null");
        Objects.requireNonNull(committedDisclosureEvents, "disclosure events must not be null");
        if (currentTurn < 0) throw new IllegalArgumentException("current turn must not be negative");
        List<ProjectionAudit> audit = new ArrayList<>();
        List<RuntimeEvidence> safeStory = filter(RuntimeEvidenceType.STORYBOOK, storybook, allowedDocuments,
                expectedVersions, committedDisclosureEvents, currentTurn, audit);
        List<RuntimeEvidence> safeRules = filter(RuntimeEvidenceType.RULEBOOK, rulebook, allowedDocuments,
                expectedVersions, committedDisclosureEvents, currentTurn, audit);
        List<RuntimeEvidence> safeResolution = filter(RuntimeEvidenceType.RESOLUTION, resolution, allowedDocuments,
                expectedVersions, committedDisclosureEvents, currentTurn, audit);
        audit.forEach(item -> LOG.info("gm_model_projection documentId={} evidenceType={} decision={}",
                item.documentId(), item.evidenceType(), item.decision()));
        return new ModelInputProjection(safeStory, safeRules, safeResolution, continuity, audit);
    }

    public String promptText() {
        return "continuity=" + continuity + "; storybook=" + safeText(storybook)
                + "; rulebook=" + safeText(rulebook) + "; resolution=" + safeText(resolution);
    }

    private static List<RuntimeEvidence> filter(RuntimeEvidenceType expected, List<RuntimeEvidence> input,
            Set<UUID> allowedDocuments, Map<UUID, Long> expectedVersions, Set<String> events,
            long currentTurn, List<ProjectionAudit> audit) {
        Objects.requireNonNull(input, expected + " evidence must not be null");
        List<RuntimeEvidence> result = new ArrayList<>();
        for (RuntimeEvidence item : input) {
            if (item == null || item.evidenceType() != expected
                    || !allowedDocuments.contains(item.knowledgeDocumentId().value())) {
                if (item != null) audit.add(new ProjectionAudit(item.knowledgeDocumentId().value(), expected, "REJECTED_SCOPE"));
                if (item != null && !allowedDocuments.contains(item.knowledgeDocumentId().value())) {
                    throw new IllegalArgumentException("evidence document is outside model scope");
                }
                continue;
            }
            Long expectedVersion = expectedVersions.get(item.knowledgeDocumentId().value());
            if (expectedVersion != null && item.extractionVersion() != expectedVersion) {
                audit.add(new ProjectionAudit(item.knowledgeDocumentId().value(), expected, "REJECTED_VERSION"));
                throw new IllegalArgumentException("evidence extraction version is outside model scope");
            }
            if (item.extractionVersion() <= 0) throw new IllegalArgumentException("evidence version is missing");
            boolean visible = item.visibility() == StoryEvidenceVisibility.PLAYER_VISIBLE
                    || item.visibility() == StoryEvidenceVisibility.PUBLIC_SUMMARY
                    || ((item.visibility() == StoryEvidenceVisibility.DISCOVERED
                    || item.visibility() == StoryEvidenceVisibility.REVEALED_AFTER_EVENT)
                    && item.disclosureEvent() != null && events.contains(item.disclosureEvent())
                    && currentTurn >= item.disclosureTurn());
            if (visible) {
                result.add(item);
                audit.add(new ProjectionAudit(item.knowledgeDocumentId().value(), expected, "ACCEPTED"));
            } else {
                audit.add(new ProjectionAudit(item.knowledgeDocumentId().value(), expected, "REJECTED_HIDDEN"));
            }
        }
        return result;
    }

    private static String safeText(List<RuntimeEvidence> evidence) {
        return evidence.stream().map(item -> item.locator() + ":" + item.excerpt()).toList().toString();
    }

    public record ProjectionAudit(UUID documentId, RuntimeEvidenceType evidenceType, String decision) {}

    public static List<String> redactProtectedTurns(List<String> turns, List<RuntimeEvidence> authoritativeEvidence) {
        return turns.stream().map(turn -> authoritativeEvidence.stream()
                .filter(evidence -> evidence.visibility() == StoryEvidenceVisibility.GM_ONLY
                        || evidence.visibility() == StoryEvidenceVisibility.NPC_PRIVATE)
                .anyMatch(evidence -> turn.toLowerCase(java.util.Locale.ROOT)
                        .contains(evidence.excerpt().toLowerCase(java.util.Locale.ROOT)))
                ? "[protected turn redacted]" : turn).toList();
    }
}
