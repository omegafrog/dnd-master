package com.dndmaster.gmeval.tuning;

import com.dndmaster.gmeval.registry.DatasetSplit;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Gate for evidence readiness only. It never trains or changes runtime configuration. */
public final class TuningReadinessGate {
    private static final int MIN_REPEATED_EVIDENCE = 2;

    public TuningEligibility evaluate(TuningProposal proposal) {
        Objects.requireNonNull(proposal, "tuning proposal required");
        EnumSet<TuningRejectionReason> reasons = EnumSet.noneOf(TuningRejectionReason.class);
        EnumSet<TuningFailureCategory> taxonomy = EnumSet.noneOf(TuningFailureCategory.class);
        for (FailureEvidence evidence : proposal.failureEvidence()) taxonomy.add(evidence.category());
        if (!proposal.stableContractPresent() || !proposal.evalPresent() || !proposal.baselinePresent()
                || !proposal.optimizedPromptPresent()) reasons.add(TuningRejectionReason.MISSING_PREREQUISITE);
        if (proposal.comparison().role() != proposal.role()
                || !proposal.comparison().evalVersion().equals(proposal.evalVersion())
                || !proposal.comparison().holdoutVersion().equals(proposal.holdoutVersion())) {
            reasons.add(TuningRejectionReason.ROLE_MISMATCH);
        }
        if (!proposal.comparison().baseModelVersion().equals(proposal.baseModelVersion())
                || !proposal.comparison().optimizedPromptVersion().equals(proposal.optimizedPromptVersion())) {
            reasons.add(TuningRejectionReason.COMPARISON_MISMATCH);
        }
        if (!hasRepeatedEvidence(proposal.failureEvidence())) reasons.add(TuningRejectionReason.INSUFFICIENT_FAILURE_EVIDENCE);
        if (proposal.samples().isEmpty()) reasons.add(TuningRejectionReason.MISSING_PREREQUISITE);

        List<SampleExclusion> exclusions = new ArrayList<>();
        Map<String, Set<DatasetSplit>> entitySplits = splitOwnership(proposal.samples());
        for (TuningSample sample : proposal.samples()) {
            if (sample.role() != proposal.role()) {
                reasons.add(TuningRejectionReason.ROLE_MISMATCH);
                exclusions.add(new SampleExclusion(sample.sampleId(), TuningRejectionReason.ROLE_MISMATCH,
                        "sample role does not match proposal role"));
            } else if (!sample.datasetVersion().equals(proposal.datasetVersion())) {
                exclusions.add(new SampleExclusion(sample.sampleId(), TuningRejectionReason.PROVENANCE_INVALID,
                        "sample dataset version does not match proposal"));
            } else if (sample.unsafe()) {
                exclusions.add(new SampleExclusion(sample.sampleId(), TuningRejectionReason.UNSAFE_SAMPLE,
                        "permission, curation, or unresolved unsafe failure defect"));
            } else if (leaksAcrossSplits(sample, entitySplits)) {
                exclusions.add(new SampleExclusion(sample.sampleId(), TuningRejectionReason.SPLIT_LEAKAGE,
                        "adventure or session occurs in more than one split"));
            } else if (sample.split() != DatasetSplit.TRAIN) {
                exclusions.add(new SampleExclusion(sample.sampleId(), TuningRejectionReason.NON_TRAIN_SAMPLE,
                        "only train samples may enter tuning data"));
            }
        }
        Set<String> excluded = exclusions.stream().map(SampleExclusion::sampleId).collect(Collectors.toSet());
        List<TuningSample> eligibleSamples = proposal.samples().stream()
                .filter(value -> !excluded.contains(value.sampleId())).toList();
        if (eligibleSamples.isEmpty()) reasons.add(TuningRejectionReason.NO_ELIGIBLE_SAMPLES);

        TuningProposalStatus status;
        if (reasons.contains(TuningRejectionReason.MISSING_PREREQUISITE)
                || reasons.contains(TuningRejectionReason.INSUFFICIENT_FAILURE_EVIDENCE)) {
            status = TuningProposalStatus.NOT_JUSTIFIED;
        } else if (!reasons.isEmpty()) {
            status = TuningProposalStatus.REJECTED;
        } else {
            status = TuningProposalStatus.ELIGIBLE;
        }
        return new TuningEligibility(proposal.proposalId(), proposal.role(), status, reasons,
                eligibleSamples, exclusions, taxonomy, fingerprint(proposal, status, reasons, eligibleSamples, exclusions));
    }

    private static boolean hasRepeatedEvidence(List<FailureEvidence> evidence) {
        Map<TuningFailureCategory, Set<String>> ids = new EnumMap<>(TuningFailureCategory.class);
        for (FailureEvidence value : evidence) ids.computeIfAbsent(value.category(), ignored -> new HashSet<>()).add(value.evidenceId());
        return ids.values().stream().anyMatch(value -> value.size() >= MIN_REPEATED_EVIDENCE);
    }

    private static Map<String, Set<DatasetSplit>> splitOwnership(List<TuningSample> samples) {
        Map<String, Set<DatasetSplit>> result = new HashMap<>();
        for (TuningSample sample : samples) {
            result.computeIfAbsent("adventure:" + sample.provenance().adventureId(), ignored -> EnumSet.noneOf(DatasetSplit.class)).add(sample.split());
            result.computeIfAbsent("session:" + sample.provenance().sessionId(), ignored -> EnumSet.noneOf(DatasetSplit.class)).add(sample.split());
        }
        return result;
    }

    private static boolean leaksAcrossSplits(TuningSample sample, Map<String, Set<DatasetSplit>> ownership) {
        return ownership.get("adventure:" + sample.provenance().adventureId()).size() > 1
                || ownership.get("session:" + sample.provenance().sessionId()).size() > 1;
    }

    private static String fingerprint(TuningProposal proposal, TuningProposalStatus status,
                                      Set<TuningRejectionReason> reasons, List<TuningSample> eligible,
                                      List<SampleExclusion> exclusions) {
        String canonical = proposal.proposalId() + "|" + proposal.role() + "|" + proposal.method() + "|"
                + proposal.stableContractVersion() + "|" + proposal.evalVersion() + "|" + proposal.datasetVersion()
                + "|" + proposal.holdoutVersion() + "|" + proposal.baseModelVersion() + "|"
                + proposal.optimizedPromptVersion() + "|" + status + "|" + reasons + "|"
                + eligible.stream().map(TuningSample::sampleId).sorted().collect(Collectors.joining(",")) + "|" + exclusions;
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
}
