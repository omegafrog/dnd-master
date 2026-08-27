package com.dndmaster.adventure.application.storyplan;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/** Immutable authorization boundary for a complete projection repair. */
public record RepairScope(Set<String> blockerPaths, Set<String> dependentPaths, Set<String> allowedPaths,
        boolean regenerationRequired) {
    public RepairScope {
        blockerPaths = immutable(blockerPaths);
        dependentPaths = immutable(dependentPaths);
        Set<String> computed = new TreeSet<>(blockerPaths);
        computed.addAll(dependentPaths);
        if (allowedPaths != null) computed.addAll(allowedPaths);
        allowedPaths = Set.copyOf(computed);
    }

    public RepairScope(Set<String> blockerPaths, Set<String> dependentPaths, Set<String> allowedPaths) {
        this(blockerPaths, dependentPaths, allowedPaths, false);
    }

    public boolean allows(String path) {
        String actual = normalize(path);
        return allowedPaths.stream().anyMatch(allowed -> matches(allowed, actual));
    }

    public boolean isRepairable() {
        return !regenerationRequired;
    }

    private static Set<String> immutable(Set<String> values) {
        Objects.requireNonNull(values, "repair scope paths must not be null");
        return Set.copyOf(new LinkedHashSet<>(values.stream().map(RepairScope::normalize).toList()));
    }

    static String normalize(String path) {
        return path == null ? "" : path.replaceFirst("^\\$", "").replaceFirst("^\\.", "");
    }

    private static boolean matches(String allowed, String actual) {
        if (allowed.isBlank() || actual.isBlank()) return allowed.equals(actual);
        if (allowed.equals(actual) || actual.startsWith(allowed + ".") || actual.startsWith(allowed + "[")) return true;
        StringBuilder regex = new StringBuilder();
        for (int index = 0; index < allowed.length();) {
            if (allowed.startsWith("[*]", index)) {
                regex.append("\\[\\d+\\]");
                index += 3;
            } else {
                regex.append(Pattern.quote(String.valueOf(allowed.charAt(index++))));
            }
        }
        if (allowed.endsWith("[*]")) regex.append("(?:\\..*)?");
        return actual.matches(regex.toString());
    }
}
