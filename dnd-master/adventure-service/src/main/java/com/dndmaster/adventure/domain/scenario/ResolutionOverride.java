package com.dndmaster.adventure.domain.scenario;

import com.dndmaster.adventure.application.scenario.compilation.ResolutionCandidate;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record ResolutionOverride(
        UUID overrideId,
        ScenarioBundleId bundleId,
        OwnerPlayerId ownerPlayerId,
        long revision,
        String author,
        String reason,
        Instant createdAt,
        Instant updatedAt,
        ResolutionOverrideStatus status,
        String anchorFingerprint,
        String documentFingerprint,
        String contentFingerprint,
        String quoteFingerprint,
        String contextFingerprint,
        String locatorFingerprint,
        String unitFingerprint,
        ResolutionCandidate replacementCandidate,
        List<ResolutionOverrideRevision> revisions) {
    public ResolutionOverride {
        overrideId = Objects.requireNonNull(overrideId, "overrideId must not be null");
        bundleId = Objects.requireNonNull(bundleId, "bundleId must not be null");
        ownerPlayerId = Objects.requireNonNull(ownerPlayerId, "ownerPlayerId must not be null");
        if (revision <= 0) {
            throw new IllegalArgumentException("revision must be positive");
        }
        author = Objects.requireNonNull(author, "author must not be null");
        reason = Objects.requireNonNull(reason, "reason must not be null");
        createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        status = Objects.requireNonNull(status, "status must not be null");
        anchorFingerprint = Objects.requireNonNull(anchorFingerprint, "anchorFingerprint must not be null");
        documentFingerprint = Objects.requireNonNull(documentFingerprint, "documentFingerprint must not be null");
        contentFingerprint = Objects.requireNonNull(contentFingerprint, "contentFingerprint must not be null");
        quoteFingerprint = Objects.requireNonNull(quoteFingerprint, "quoteFingerprint must not be null");
        contextFingerprint = Objects.requireNonNull(contextFingerprint, "contextFingerprint must not be null");
        locatorFingerprint = Objects.requireNonNull(locatorFingerprint, "locatorFingerprint must not be null");
        unitFingerprint = Objects.requireNonNull(unitFingerprint, "unitFingerprint must not be null");
        replacementCandidate = Objects.requireNonNull(replacementCandidate, "replacementCandidate must not be null");
        revisions = List.copyOf(Objects.requireNonNull(revisions, "revisions must not be null"));
        if (revisions.isEmpty()) {
            throw new IllegalArgumentException("revisions must not be empty");
        }
    }

    public static ResolutionOverride create(
            ScenarioBundleId bundleId,
            OwnerPlayerId ownerPlayerId,
            String author,
            String reason,
            ResolutionCandidate originalCandidate,
            ResolutionCandidate replacementCandidate,
            Instant createdAt,
            Instant updatedAt,
            ResolutionOverrideStatus status,
            long revision) {
        String documentFingerprint = ResolutionFingerprint.candidateDocumentFingerprint(originalCandidate.sourceRefs());
        String locatorFingerprint = ResolutionFingerprint.candidateLocatorFingerprint(originalCandidate.sourceRefs());
        String quoteFingerprint = ResolutionFingerprint.candidateQuoteFingerprint(originalCandidate.sourceQuote());
        String contextFingerprint = ResolutionFingerprint.candidateContextFingerprint(originalCandidate.detail());
        String unitFingerprint = ResolutionFingerprint.candidateUnitFingerprint(
                originalCandidate.kind(), originalCandidate.abilityOrSkill(), originalCandidate.dc(),
                originalCandidate.diceExpression(), originalCandidate.visibility(), originalCandidate.detail());
        String contentFingerprint = ResolutionFingerprint.candidateFingerprint(
                originalCandidate.sourceQuote() + "|" + originalCandidate.detail());
        String anchorFingerprint = ResolutionFingerprint.overrideAnchorFingerprint(
                new ResolutionFingerprint.ResolutionCandidateSnapshot(
                        documentFingerprint, locatorFingerprint, quoteFingerprint, contextFingerprint, unitFingerprint));
        return new ResolutionOverride(
                UUID.randomUUID(),
                bundleId,
                ownerPlayerId,
                revision,
                author,
                reason,
                createdAt,
                updatedAt,
                status,
                anchorFingerprint,
                documentFingerprint,
                contentFingerprint,
                quoteFingerprint,
                contextFingerprint,
                locatorFingerprint,
                unitFingerprint,
                replacementCandidate,
                List.of(new ResolutionOverrideRevision(revision, author, reason, createdAt, updatedAt, status)));
    }

    public ResolutionOverride withRevision(
            long nextRevision,
            String author,
            String reason,
            ResolutionCandidate replacementCandidate,
            Instant updatedAt,
            ResolutionOverrideStatus status) {
        List<ResolutionOverrideRevision> nextRevisions = new java.util.ArrayList<>(revisions);
        nextRevisions.add(new ResolutionOverrideRevision(nextRevision, author, reason, createdAt, updatedAt, status));
        return new ResolutionOverride(
                overrideId, bundleId, ownerPlayerId, nextRevision, author, reason, createdAt, updatedAt, status,
                anchorFingerprint, documentFingerprint, contentFingerprint, quoteFingerprint, contextFingerprint,
                locatorFingerprint, unitFingerprint, replacementCandidate, nextRevisions);
    }

    public ResolutionOverride withStatus(
            String reason,
            Instant updatedAt,
            ResolutionOverrideStatus status,
            ResolutionCandidate replacementCandidate) {
        return new ResolutionOverride(
                overrideId, bundleId, ownerPlayerId, revision, author, reason, createdAt, updatedAt, status,
                anchorFingerprint, documentFingerprint, contentFingerprint, quoteFingerprint, contextFingerprint,
                locatorFingerprint, unitFingerprint, replacementCandidate, revisions);
    }

    public String candidateFingerprint() {
        return ResolutionFingerprint.candidateAnchorFingerprint(
                replacementCandidate.kind(),
                replacementCandidate.abilityOrSkill(),
                replacementCandidate.dc(),
                replacementCandidate.diceExpression(),
                replacementCandidate.visibility(),
                replacementCandidate.sourceQuote(),
                replacementCandidate.sourceRefs(),
                replacementCandidate.detail());
    }
}
