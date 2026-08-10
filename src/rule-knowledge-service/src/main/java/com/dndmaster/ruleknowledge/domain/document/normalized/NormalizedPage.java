package com.dndmaster.ruleknowledge.domain.document.normalized;

public record NormalizedPage(int number, Double width, Double height) {
    public NormalizedPage {
        if (number < 1) throw new IllegalArgumentException("page number must be positive");
    }
}
