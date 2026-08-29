package com.dndmaster.gmeval.optimization;

import com.dndmaster.gmeval.registry.PromptRole;
import com.dndmaster.gmeval.registry.PromptVersion;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/** Read projection for operators. Fingerprint excludes wall-clock data by design. */
public record PromptRunReport(String runId, PromptRole role, String datasetVersion, String evalVersion,
                              long seed, PromptVersion baselinePromptVersion, List<PromptCandidateEvaluation> candidates,
                              String selectedCandidateId, String reportFingerprint) {
    public PromptRunReport {
        if (runId == null || runId.isBlank()) throw new IllegalArgumentException("run id required");
        role = Objects.requireNonNull(role, "prompt role required");
        if (datasetVersion == null || datasetVersion.isBlank()) throw new IllegalArgumentException("dataset version required");
        if (evalVersion == null || evalVersion.isBlank()) throw new IllegalArgumentException("eval version required");
        baselinePromptVersion = Objects.requireNonNull(baselinePromptVersion, "baseline prompt version required");
        if (baselinePromptVersion.role() != role) throw new IllegalArgumentException("baseline role mismatch");
        candidates = List.copyOf(candidates == null ? List.of() : candidates);
        if (candidates.isEmpty()) throw new IllegalArgumentException("candidate reports required");
        for (PromptCandidateEvaluation candidate : candidates) {
            if (candidate.candidate().role() != role) throw new IllegalArgumentException("candidate role mismatch");
        }
        if (selectedCandidateId != null && candidates.stream().noneMatch(value -> value.candidate().candidateId().equals(selectedCandidateId))) {
            throw new IllegalArgumentException("selected candidate is not in report");
        }
        reportFingerprint = reportFingerprint == null || reportFingerprint.isBlank()
                ? fingerprint(runId, role, datasetVersion, evalVersion, seed, baselinePromptVersion, candidates, selectedCandidateId)
                : reportFingerprint;
    }

    public static PromptRunReport create(String runId, PromptRole role, String datasetVersion, String evalVersion,
                                         long seed, PromptVersion baselinePromptVersion,
                                         List<PromptCandidateEvaluation> candidates, String selectedCandidateId) {
        return new PromptRunReport(runId, role, datasetVersion, evalVersion, seed, baselinePromptVersion,
                candidates, selectedCandidateId, null);
    }

    private static String fingerprint(String runId, PromptRole role, String datasetVersion, String evalVersion,
                                      long seed, PromptVersion baseline, List<PromptCandidateEvaluation> candidates,
                                      String selected) {
        String canonical = runId + "|" + role + "|" + datasetVersion + "|" + evalVersion + "|" + seed + "|"
                + baseline.role() + ":" + baseline.value() + "|" + selected + "|"
                + candidates.stream().map(PromptRunReport::canonical).sorted().collect(Collectors.joining(";"));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static String canonical(PromptCandidateEvaluation value) {
        PromptCandidate candidate = value.candidate();
        return candidate.candidateId() + "|" + candidate.role() + "|" + candidate.seed() + "|"
                + candidate.promptArtifact().promptVersion().value() + "|" + candidate.promptArtifact().modelVersion()
                + "|" + value.metrics().hardViolations().entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue()).sorted().collect(Collectors.joining(","))
                + "|" + value.metrics().softScores().entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue()).sorted().collect(Collectors.joining(","))
                + "|" + value.baselineDelta().hardViolationDelta().entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue()).sorted().collect(Collectors.joining(","))
                + "|" + value.baselineDelta().softScoreDelta().entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue()).sorted().collect(Collectors.joining(","))
                + "|" + value.gate() + "|" + value.representativeOutputs();
    }
}
