package com.dndmaster.gmeval.registry;

import com.dndmaster.gmeval.optimization.PromptCandidateEvaluation;
import com.dndmaster.gmeval.optimization.PromptOptimizationRun;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Role-isolated registry enforcing approval evidence, CAS activation, rollback, and audit. */
public final class PromptRegistry implements PromptRegistryReadPort {
    private final PromptRegistryStore store;
    private final PromptApprovalStore approvalStore;
    private final PromptAuditStore auditStore;
    private final Map<PromptVersion, PromptArtifact> artifacts = new LinkedHashMap<>();
    private final Map<PromptVersion, PromptApprovalRecord> approvals = new LinkedHashMap<>();
    private final List<PromptAuditEntry> standaloneAudit = new ArrayList<>();

    public PromptRegistry() { this(new InMemoryPromptRegistryStore(), new InMemoryPromptApprovalStore(), new InMemoryPromptAuditStore()); }
    public PromptRegistry(PromptRegistryStore store) { this(store, new InMemoryPromptApprovalStore(), new InMemoryPromptAuditStore()); }
    public PromptRegistry(PromptRegistryStore store, PromptApprovalStore approvalStore) { this(store, approvalStore, new InMemoryPromptAuditStore()); }

    public PromptRegistry(PromptRegistryStore store, PromptApprovalStore approvalStore, PromptAuditStore auditStore) {
        this.store = Objects.requireNonNull(store, "prompt registry store required");
        this.approvalStore = Objects.requireNonNull(approvalStore, "prompt approval store required");
        this.auditStore = Objects.requireNonNull(auditStore, "prompt audit store required");
        for (PromptArtifact artifact : store.load()) {
            if (artifacts.put(artifact.promptVersion(), artifact) != null) throw new IllegalArgumentException("duplicate prompt version in registry");
        }
        for (PromptApprovalRecord approval : approvalStore.load()) {
            if (approvals.put(approval.promptVersion(), approval) != null) throw new IllegalArgumentException("duplicate prompt approval");
        }
        standaloneAudit.addAll(auditStore.load());
        if (standaloneAudit.isEmpty()) standaloneAudit.addAll(approvals.values().stream().flatMap(value -> value.audit().stream()).toList());
        validateActiveUniqueness();
    }

    public synchronized PromptArtifact register(PromptArtifact artifact) {
        Objects.requireNonNull(artifact, "prompt artifact required");
        if (artifact.status() == PromptArtifactStatus.ACTIVE || artifact.status() == PromptArtifactStatus.PENDING_REVIEW
                || artifact.status() == PromptArtifactStatus.APPROVED) throw new IllegalArgumentException("managed statuses must be entered through the registry");
        if (artifacts.containsKey(artifact.promptVersion())) throw new IllegalArgumentException("prompt version is immutable: " + artifact.promptVersion());
        artifacts.put(artifact.promptVersion(), artifact);
        persist();
        return artifact;
    }

    /** Baselines are trusted bootstrap configuration and do not require an optimization run. */
    public synchronized PromptArtifact registerBaseline(PromptArtifact artifact) {
        if (artifact == null || !artifact.baseline()) throw new IllegalArgumentException("baseline artifact required");
        register(artifact);
        approve(artifact.promptVersion());
        activate(artifact.promptVersion(), activeVersion(artifact.promptVersion().role()).orElse(null), "system-baseline");
        return require(artifact.promptVersion());
    }

    /** Moves the selected, hard-gated candidate into the human review queue. */
    public synchronized PromptArtifact submitForReview(PromptOptimizationRun run, String candidateId, HoldoutEvaluation holdout) {
        Objects.requireNonNull(run, "optimization run required");
        Objects.requireNonNull(holdout, "holdout evidence required");
        PromptCandidateEvaluation selected = run.selected();
        if (selected == null || !selected.candidate().candidateId().equals(candidateId)) throw new IllegalArgumentException("only the selected candidate can enter review");
        if (!selected.gate().accepted()) throw new IllegalArgumentException("hard-gated candidate required");
        if (!run.role().equals(selected.candidate().role())) throw new IllegalArgumentException("candidate role mismatch");
        if (!run.datasetVersion().equals(holdout.datasetVersion()) || !run.evalVersion().equals(holdout.evalVersion())) throw new IllegalArgumentException("holdout dataset/eval mismatch");
        if (!holdout.passed()) throw new IllegalArgumentException("holdout regression blocks review");
        PromptArtifact candidate = selected.candidate().promptArtifact();
        if (artifacts.containsKey(candidate.promptVersion())) throw new IllegalArgumentException("prompt version already registered");
        PromptArtifact pending = candidate.withOptimizationRunId(run.runId()).withStatus(PromptArtifactStatus.PENDING_REVIEW);
        artifacts.put(pending.promptVersion(), pending);
        PromptAuditEntry entry = audit(PromptAuditAction.REGISTER_FOR_REVIEW, pending, selected.candidate().candidateId(), run.runId(), "system", null, null, "holdout passed");
        approvals.put(pending.promptVersion(), new PromptApprovalRecord(pending.promptVersion(), selected.candidate().candidateId(), run.runId(), holdout, null, PromptArtifactStatus.PENDING_REVIEW, List.of(entry)));
        persist();
        return pending;
    }

    /** Records representative-sample review. Approval requires at least one reviewed sample. */
    public synchronized PromptArtifact review(PromptVersion version, ReviewerDecision decision) {
        PromptApprovalRecord approval = approval(version);
        if (approval.status() != PromptArtifactStatus.PENDING_REVIEW) throw new IllegalStateException("prompt is not pending review");
        if (decision.approved() && decision.representativeSampleIds().isEmpty()) throw new IllegalArgumentException("representative samples must be reviewed");
        PromptArtifactStatus next = decision.approved() ? PromptArtifactStatus.APPROVED : PromptArtifactStatus.PENDING_REVIEW;
        PromptArtifact artifact = require(version).withStatus(next);
        PromptAuditEntry entry = audit(PromptAuditAction.REVIEW, artifact, approval.candidateId(), approval.optimizationRunId(), decision.reviewerId(), null, null, decision.reason());
        approvals.put(version, approval.withReview(decision, next, entry));
        artifacts.put(version, artifact);
        persist();
        return artifact;
    }

    /** Compatibility API: only bootstrap baselines may bypass human review evidence. */
    public synchronized PromptArtifact approve(PromptVersion version) {
        PromptArtifact artifact = require(version);
        if (artifact.baseline()) {
            PromptArtifact approved = artifact.withStatus(PromptArtifactStatus.APPROVED);
            artifacts.put(version, approved);
            persist();
            return approved;
        }
        PromptApprovalRecord approval = approval(version);
        if (approval.status() != PromptArtifactStatus.APPROVED || approval.review() == null || !approval.review().approved() || !approval.holdout().passed()) throw new IllegalStateException("holdout and reviewer approval required");
        return artifact;
    }

    public synchronized PromptRuntimeConfiguration activate(PromptVersion version) {
        return activate(version, activeVersion(version.role()).orElse(null), "operator");
    }

    /** Atomically activates one role and rejects a stale expected active version. */
    public synchronized PromptRuntimeConfiguration activate(PromptVersion version, PromptVersion expectedActiveVersion, String actor) {
        PromptArtifact candidate = require(version);
        PromptVersion actual = activeVersion(version.role()).orElse(null);
        if (!Objects.equals(expectedActiveVersion, actual)) throw new StalePromptActivationException(version.role(), expectedActiveVersion, actual);
        if (!candidate.isApproved()) throw new IllegalStateException("prompt must be approved before activation");
        if (!candidate.baseline()) {
            PromptApprovalRecord approval = approval(version);
            if (approval.status() != PromptArtifactStatus.APPROVED && approval.status() != PromptArtifactStatus.ACTIVE) throw new IllegalStateException("holdout and reviewer approval required");
        }
        if (actual != null && !actual.equals(version)) {
            PromptArtifact previous = require(actual).withStatus(PromptArtifactStatus.APPROVED);
            artifacts.put(actual, previous);
            PromptApprovalRecord previousApproval = approvals.get(actual);
            if (previousApproval != null) approvals.put(actual, previousApproval.withStatus(PromptArtifactStatus.APPROVED,
                    audit(PromptAuditAction.ACTIVATE, previous, previousApproval.candidateId(), previousApproval.optimizationRunId(), actor, expectedActiveVersion, actual, "superseded; retained for rollback")));
        }
        PromptArtifact active = candidate.withStatus(PromptArtifactStatus.ACTIVE);
        artifacts.put(version, active);
        PromptApprovalRecord approval = approvals.get(version);
        if (approval != null) approvals.put(version, approval.withStatus(PromptArtifactStatus.ACTIVE,
                audit(PromptAuditAction.ACTIVATE, active, approval.candidateId(), approval.optimizationRunId(), actor, expectedActiveVersion, actual, "approved prompt activated")));
        persist();
        return PromptRuntimeConfiguration.from(active);
    }

    /** Explicitly returns the approved prior version to ACTIVE. */
    public synchronized PromptRuntimeConfiguration rollback(PromptRole role, PromptVersion targetVersion, PromptVersion expectedActiveVersion, String actor) {
        Objects.requireNonNull(role, "prompt role required");
        PromptVersion actual = activeVersion(role).orElse(null);
        if (!Objects.equals(expectedActiveVersion, actual)) throw new StalePromptActivationException(role, expectedActiveVersion, actual);
        PromptArtifact target = require(targetVersion);
        if (target.promptVersion().role() != role) throw new IllegalArgumentException("rollback role mismatch");
        if (target.status() != PromptArtifactStatus.APPROVED) throw new IllegalStateException("rollback target must be approved");
        if (actual == null || actual.equals(targetVersion)) throw new IllegalStateException("no active prompt to rollback");
        PromptArtifact previous = require(actual).withStatus(PromptArtifactStatus.ROLLED_BACK);
        artifacts.put(actual, previous);
        PromptApprovalRecord currentApproval = approvals.get(actual);
        if (currentApproval != null) approvals.put(actual, currentApproval.withStatus(PromptArtifactStatus.ROLLED_BACK,
                audit(PromptAuditAction.ROLLBACK, previous, currentApproval.candidateId(), currentApproval.optimizationRunId(), actor, expectedActiveVersion, actual, "active prompt rolled back")));
        PromptArtifact restored = target.withStatus(PromptArtifactStatus.ACTIVE);
        artifacts.put(targetVersion, restored);
        PromptApprovalRecord targetApproval = approvals.get(targetVersion);
        if (targetApproval != null) approvals.put(targetVersion, targetApproval.withStatus(PromptArtifactStatus.ACTIVE,
                audit(PromptAuditAction.ROLLBACK, restored, targetApproval.candidateId(), targetApproval.optimizationRunId(), actor, expectedActiveVersion, actual, "previous approved prompt restored")));
        if (targetApproval == null) audit(PromptAuditAction.ROLLBACK, restored, null, restored.optimizationRunId(), actor,
                expectedActiveVersion, actual, "previous approved prompt restored");
        persist();
        return PromptRuntimeConfiguration.from(restored);
    }

    public synchronized Optional<PromptArtifact> artifact(PromptVersion version) { return Optional.ofNullable(artifacts.get(version)); }
    public synchronized Optional<PromptApprovalRecord> approvalRecord(PromptVersion version) { return Optional.ofNullable(approvals.get(version)); }
    public synchronized List<PromptAuditEntry> audit() { return List.copyOf(standaloneAudit); }

    @Override public synchronized PromptRuntimeConfiguration active(PromptRole role) {
        Objects.requireNonNull(role, "prompt role required");
        return PromptRuntimeConfiguration.from(artifacts.values().stream().filter(value -> value.promptVersion().role() == role && value.status() == PromptArtifactStatus.ACTIVE)
                .findFirst().orElseThrow(() -> new IllegalStateException("no active approved prompt for role " + role)));
    }

    @Override public synchronized List<PromptArtifact> list() { return List.copyOf(artifacts.values()); }

    /** Explicitly blocks legacy inline prompt fallback at the registry boundary. */
    public PromptRuntimeConfiguration resolveInline(PromptRole role, String inlinePrompt) {
        throw new IllegalStateException("inline prompts are not registered; resolve an approved active artifact for " + role);
    }

    private PromptApprovalRecord approval(PromptVersion version) {
        PromptApprovalRecord value = approvals.get(Objects.requireNonNull(version, "prompt version required"));
        if (value == null) throw new IllegalStateException("prompt has no approval workflow: " + version);
        return value;
    }

    private PromptArtifact require(PromptVersion version) {
        PromptArtifact artifact = artifacts.get(Objects.requireNonNull(version, "prompt version required"));
        if (artifact == null) throw new IllegalStateException("prompt version is not registered: " + version);
        return artifact;
    }

    private Optional<PromptVersion> activeVersion(PromptRole role) {
        return artifacts.values().stream().filter(value -> value.promptVersion().role() == role && value.status() == PromptArtifactStatus.ACTIVE)
                .map(PromptArtifact::promptVersion).findFirst();
    }

    private PromptAuditEntry audit(PromptAuditAction action, PromptArtifact artifact, String candidateId, String runId, String actor,
                                   PromptVersion expected, PromptVersion previous, String reason) {
        PromptAuditEntry entry = new PromptAuditEntry(action, artifact.promptVersion().role(), artifact.promptVersion(), candidateId, runId, actor, expected, previous, reason, null);
        standaloneAudit.add(entry);
        return entry;
    }

    private void validateActiveUniqueness() {
        Map<PromptRole, Integer> activeByRole = new EnumMap<>(PromptRole.class);
        for (PromptArtifact artifact : artifacts.values()) if (artifact.status() == PromptArtifactStatus.ACTIVE
                && activeByRole.merge(artifact.promptVersion().role(), 1, Integer::sum) > 1) throw new IllegalArgumentException("multiple active prompts for role");
    }

    private void persist() {
        store.save(new ArrayList<>(artifacts.values()));
        approvalStore.save(new ArrayList<>(approvals.values()));
        auditStore.save(standaloneAudit);
    }
}
