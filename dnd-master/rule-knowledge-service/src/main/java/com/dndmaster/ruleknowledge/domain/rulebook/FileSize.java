package com.dndmaster.ruleknowledge.domain.rulebook;

public record FileSize(long bytes) {
    public FileSize {
        if (bytes <= 0) {
            throw new IllegalArgumentException("file size must be positive");
        }
    }
}
