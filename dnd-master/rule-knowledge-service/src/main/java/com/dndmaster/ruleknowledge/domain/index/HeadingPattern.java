package com.dndmaster.ruleknowledge.domain.index;

import java.util.Objects;
import java.util.regex.Pattern;

public record HeadingPattern(
        Pattern compiledPattern,
        String groupName,
        String description) {
    public HeadingPattern {
        Objects.requireNonNull(compiledPattern, "compiledPattern must not be null");
        Objects.requireNonNull(groupName, "groupName must not be null");
    }
}
