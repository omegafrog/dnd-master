package com.dndmaster.adventure.application.storyplan;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Computes the deterministic same-stage dependency closure for projection repair. */
public final class AdventureStoryPlanProjectionDependencyPolicy {
    private static final Pattern STAGE_PATH = Pattern.compile("^(stages\\[(\\d+|\\*)\\])(?:\\.(.*))?$");

    private AdventureStoryPlanProjectionDependencyPolicy() { }

    public static RepairScope scope(String fullSerializedCandidate,
            List<AdventureStoryPlanProjectionViolation> violations) {
        if (fullSerializedCandidate == null || fullSerializedCandidate.isBlank()) {
            throw new IllegalArgumentException("full candidate is required to compute repair scope");
        }
        if (violations == null || violations.isEmpty()) {
            throw new IllegalArgumentException("projection blockers are required to compute repair scope");
        }
        Set<String> blockers = new TreeSet<>();
        Set<String> dependents = new TreeSet<>();
        boolean regenerationRequired = false;
        for (AdventureStoryPlanProjectionViolation violation : violations) {
            String path = RepairScope.normalize(violation.fieldPath());
            blockers.add(path);
            regenerationRequired |= violation.repairability() != AdventureStoryPlanProjectionViolation.Repairability.REPAIRABLE;
            dependents.addAll(dependencies(path));
        }
        return new RepairScope(blockers, dependents, Set.of(), regenerationRequired);
    }

    private static Set<String> dependencies(String path) {
        Matcher matcher = STAGE_PATH.matcher(path);
        if (!matcher.matches()) return Set.of();
        String stage = matcher.group(1);
        String field = matcher.group(3) == null ? "" : matcher.group(3);
        if (!isCombatDependency(field)) return Set.of();
        Matcher participant = Pattern.compile("^combatSkeleton\\.participants\\[(\\d+|\\*)\\]\\.(.+)$").matcher(field);
        if (participant.matches()) {
            String item = stage + ".combatSkeleton.participants[" + participant.group(1) + "]";
            return Set.of(stage + ".combatRequirement", item + "." + participant.group(2),
                    item + ".citationKeys", stage + ".evidence[*].citationKey");
        }
        List<String> paths = new ArrayList<>();
        paths.add(stage + ".combatRequirement");
        paths.add(stage + ".combatSkeleton.objective");
        paths.add(stage + ".combatSkeleton.startTrigger");
        paths.add(stage + ".combatSkeleton.successOutcome");
        paths.add(stage + ".combatSkeleton.failureOutcome");
        paths.add(stage + ".combatSkeleton.participants");
        paths.add(stage + ".combatSkeleton.participants[*]");
        paths.add(stage + ".combatSkeleton.rewards");
        paths.add(stage + ".combatSkeleton.rewards[*]");
        paths.add(stage + ".sourceFactClaims");
        paths.add(stage + ".sourceFactClaims[*]");
        paths.add(stage + ".tacticalPreparationRequirement");
        return Set.copyOf(paths);
    }

    private static boolean isCombatDependency(String field) {
        return field.equals("combatRequirement") || field.startsWith("combatSkeleton")
                || field.equals("sourceFactClaims") || field.startsWith("sourceFactClaims[")
                || field.equals("tacticalPreparationRequirement")
                || field.equals("mapDefinitionId") || field.equals("mapAssetId") || field.equals("mapAssetLocator");
    }
}
