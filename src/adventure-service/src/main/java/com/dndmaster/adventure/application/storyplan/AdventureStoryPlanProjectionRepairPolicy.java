package com.dndmaster.adventure.application.storyplan;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/** Deterministic guard for bounded full-candidate repairs. */
public final class AdventureStoryPlanProjectionRepairPolicy {
    private AdventureStoryPlanProjectionRepairPolicy() { }

    public static void assertOnlyListedFieldsChanged(JsonNode previous, JsonNode repaired,
            List<AdventureStoryPlanProjectionViolation> violations) {
        Set<String> allowed = violations.stream().map(AdventureStoryPlanProjectionViolation::fieldPath)
                .map(AdventureStoryPlanProjectionRepairPolicy::normalizePath).collect(java.util.stream.Collectors.toSet());
        List<String> changed = new ArrayList<>();
        collectChanges(previous, repaired, "", changed);
        for (String path : changed) {
            if (allowed.stream().noneMatch(candidate -> matchesPath(candidate, normalizePath(path)))) {
                throw new UnlistedFieldMutation(new AdventureStoryPlanProjectionViolation(
                        "UNLISTED_FIELD_MUTATION", stagePosition(path), path, "[redacted]", "",
                        AdventureStoryPlanProjectionViolation.Repairability.SYSTEM_CONTRACT_ERROR,
                        "repair changed a field that was not listed in the projection violations"));
            }
        }
    }

    public static String fingerprint(String candidate, List<AdventureStoryPlanProjectionViolation> violations) {
        String input = candidate + "\n" + violations.stream()
                .map(item -> item.code() + "|" + item.stagePosition() + "|" + item.fieldPath() + "|"
                        + item.repairability() + "|" + item.citationContext())
                .sorted().collect(java.util.stream.Collectors.joining("\n"));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte value : digest) result.append(String.format("%02x", value));
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static void collectChanges(JsonNode previous, JsonNode repaired, String path, List<String> changed) {
        if (previous == null || repaired == null || previous.isMissingNode() || repaired.isMissingNode()) {
            changed.add(path);
            return;
        }
        if (previous.isObject() && repaired.isObject()) {
            Set<String> fields = new TreeSet<>();
            previous.fieldNames().forEachRemaining(fields::add);
            repaired.fieldNames().forEachRemaining(fields::add);
            for (String field : fields) {
                collectChanges(previous.get(field), repaired.get(field), childPath(path, field), changed);
            }
            return;
        }
        if (previous.isArray() && repaired.isArray()) {
            int common = Math.min(previous.size(), repaired.size());
            for (int index = 0; index < common; index++) {
                collectChanges(previous.get(index), repaired.get(index), path + "[" + index + "]", changed);
            }
            if (previous.size() != repaired.size()) changed.add(path);
            return;
        }
        if (!previous.equals(repaired)) changed.add(path);
    }

    private static String normalizePath(String path) {
        return path == null ? "" : path.replaceFirst("^\\$", "").replaceFirst("^\\.", "");
    }

    private static boolean matchesPath(String allowed, String actual) {
        if (allowed.equals(actual)) return true;
        StringBuilder pattern = new StringBuilder();
        for (int index = 0; index < allowed.length();) {
            if (allowed.startsWith("[*]", index)) {
                pattern.append("\\[\\d+\\]");
                index += 3;
            } else {
                pattern.append(Pattern.quote(String.valueOf(allowed.charAt(index++))));
            }
        }
        return actual.matches(pattern.toString());
    }

    private static String childPath(String parent, String field) {
        return parent == null || parent.isEmpty() ? field : parent + "." + field;
    }

    private static Integer stagePosition(String path) {
        if (path == null) return null;
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("stages\\[(\\d+)]").matcher(path);
        return matcher.find() ? Integer.valueOf(matcher.group(1)) + 1 : null;
    }

    public static final class UnlistedFieldMutation extends RuntimeException {
        private final AdventureStoryPlanProjectionViolation violation;

        public UnlistedFieldMutation(AdventureStoryPlanProjectionViolation violation) {
            super(violation.sanitizedMessage());
            this.violation = violation;
        }

        public AdventureStoryPlanProjectionViolation violation() {
            return violation;
        }
    }
}
