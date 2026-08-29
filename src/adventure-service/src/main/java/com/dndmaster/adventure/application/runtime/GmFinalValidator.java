package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.adventure.AdventureContext;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Fail-closed boundary between provider output and player-visible narration. */
public final class GmFinalValidator {
    public GmPlanResult validate(
            GmPlanResult result, EvidencePack evidencePack, AdventureContext currentContext, Set<String> hiddenData) {
        GmValidationReport report = validateReport(result, evidencePack, currentContext, hiddenData);
        if (!report.passed()) {
            String codes = report.violations().stream().map(GmValidationViolation::code).distinct().toList().toString();
            throw new IllegalStateException("GM final validation failed: " + codes);
        }
        return result;
    }

    public GmValidationReport validateReport(
            GmPlanResult result, EvidencePack evidencePack, AdventureContext currentContext, Set<String> hiddenData) {
        Objects.requireNonNull(result, "result must not be null");
        Objects.requireNonNull(evidencePack, "evidence pack must not be null");
        Objects.requireNonNull(currentContext, "current context must not be null");
        hiddenData = Set.copyOf(Objects.requireNonNull(hiddenData, "hidden data must not be null"));

        List<GmValidationViolation> violations = new ArrayList<>();
        RuntimePlan plan = result.plan();
        List<RuntimeEvidence> allowed = evidencePack.all();
        EnumMap<RuntimeEvidenceType, Integer> evidenceByType = new EnumMap<>(RuntimeEvidenceType.class);
        for (RuntimeEvidenceType type : RuntimeEvidenceType.values()) {
            evidenceByType.put(type, (int) allowed.stream().filter(evidence -> evidence.evidenceType() == type).count());
        }

        for (int index = 0; index < plan.citedEvidence().size(); index++) {
            if (!allowed.contains(plan.citedEvidence().get(index))) {
                violations.add(violation("CITATION_NOT_IN_EVIDENCE_PACK", "citedEvidence[" + index + "]", true,
                        "citation is outside the selected evidence pack"));
            }
        }
        if (!evidencePack.storybook().isEmpty() && plan.citedEvidence().stream()
                .noneMatch(evidence -> evidence.evidenceType() == RuntimeEvidenceType.STORYBOOK)) {
            violations.add(violation("STORYBOOK_CITATION_REQUIRED", "citedEvidence", true,
                    "storybook evidence must be cited for every GM turn"));
        }
        if (!Objects.equals(plan.scene(), currentContext.currentScene()) && plan.citedEvidence().stream()
                .noneMatch(evidencePack.storybook()::contains)) {
            violations.add(violation("SCENE_TRANSITION_UNSUPPORTED", "scene", true,
                    "scene transition requires a storybook citation"));
        }
        if (plan.proposedActiveSourceContext() != null && allowed.stream().noneMatch(evidence ->
                evidence.knowledgeDocumentId().equals(plan.proposedActiveSourceContext().knowledgeDocumentId())
                        && evidence.extractionVersion() == plan.proposedActiveSourceContext().extractionVersion()
                        && evidence.locator().equals(plan.proposedActiveSourceContext().locator()))) {
            violations.add(violation("ACTIVE_SOURCE_NOT_IN_EVIDENCE_PACK", "proposedActiveSourceContext", true,
                    "active source is outside the selected evidence pack"));
        }
        String combined = (plan.judgment() + " " + plan.narration()).toLowerCase(Locale.ROOT);
        boolean ruleClaim = combined.contains("rule") || combined.contains("must") || combined.contains("roll")
                || combined.contains("damage") || combined.contains("check") || combined.contains("판정")
                || combined.contains("규칙") || combined.contains("굴림");
        if (ruleClaim && plan.citedEvidence().isEmpty()) {
            violations.add(violation("RULE_CLAIM_REQUIRES_CITATION", "citedEvidence", true,
                    "rule claims require a citation"));
        }
        for (String secret : hiddenData) {
            if (secret != null && !secret.isBlank() && plan.narration().contains(secret)) {
                violations.add(violation("HIDDEN_DATA_IN_NARRATION", "narration", false,
                        "GM narration contains hidden data"));
            }
        }

        int claimSupportCount = validateBindings(plan, allowed, violations);
        return new GmValidationReport(violations, allowed.size(), claimSupportCount, evidenceByType);
    }

    private static int validateBindings(RuntimePlan plan, List<RuntimeEvidence> allowed,
                                        List<GmValidationViolation> violations) {
        if (plan.citationBindings().isEmpty()) return 0;
        Map<String, RuntimeEvidence> byKey = new HashMap<>();
        for (RuntimeEvidence evidence : allowed) {
            if (evidence.citationKey() != null) byKey.putIfAbsent(evidence.citationKey(), evidence);
        }
        int supported = 0;
        for (int index = 0; index < plan.citationBindings().size(); index++) {
            GmCitationBinding binding = plan.citationBindings().get(index);
            String path = "citationBindings[" + index + "]";
            RuntimeEvidence evidence = byKey.get(binding.citationKey());
            if (evidence == null) {
                violations.add(violation("CITATION_NOT_IN_EVIDENCE_PACK", path + ".citationKey", true,
                        "citation key is not present in the selected evidence pack"));
                continue;
            }
            String output = outputValue(plan, binding.outputField());
            if (output == null || !containsClaim(output, binding.claimText())) {
                violations.add(violation("CLAIM_NOT_IN_OUTPUT", path + ".claimText", true,
                        "bound claim does not appear in the selected output field"));
                continue;
            }
            if (!supports(binding.claimText(), evidence.excerpt())) {
                violations.add(violation("UNSUPPORTED_CLAIM_CITATION", path, true,
                        "citation evidence does not support the bound claim"));
                continue;
            }
            supported++;
        }
        return supported;
    }

    private static String outputValue(RuntimePlan plan, String outputField) {
        return switch (outputField) {
            case "scene" -> plan.scene();
            case "npcState" -> plan.npcState();
            case "judgment" -> plan.judgment();
            case "narration" -> plan.narration();
            default -> null;
        };
    }

    private static boolean containsClaim(String output, String claim) {
        return output.contains(claim) || supports(claim, output);
    }

    private static boolean supports(String claim, String excerpt) {
        Set<String> claimTokens = tokens(claim);
        Set<String> excerptTokens = tokens(excerpt);
        if (claimTokens.isEmpty()) return false;
        long overlap = claimTokens.stream().filter(excerptTokens::contains).count();
        return overlap >= Math.min(2, claimTokens.size()) || excerptTokens.containsAll(claimTokens);
    }

    private static Set<String> tokens(String value) {
        if (value == null) return Set.of();
        return java.util.Arrays.stream(value.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
                .map(String::trim).filter(token -> token.length() > 1).collect(java.util.stream.Collectors.toSet());
    }

    private static GmValidationViolation violation(String code, String path, boolean repairable, String message) {
        return new GmValidationViolation(code, path, repairable, message);
    }
}
