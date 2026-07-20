package com.dndmaster.ruleknowledge.domain.rulebook;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class ExtractionResult {
    private final ExtractionStatus status;
    private final String content;
    private final List<String> missingLocations;
    private final ExtractionFailure failure;
    private final boolean confirmedByPlayer;

    private ExtractionResult(
            ExtractionStatus status,
            String content,
            List<String> missingLocations,
            ExtractionFailure failure,
            boolean confirmedByPlayer) {
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.content = content;
        this.missingLocations = List.copyOf(missingLocations);
        this.failure = failure;
        this.confirmedByPlayer = confirmedByPlayer;
        validate();
    }

    public static ExtractionResult success(String content) {
        return new ExtractionResult(ExtractionStatus.SUCCESS, normalizedContent(content), List.of(), null, false);
    }

    public static ExtractionResult partial(String content, List<String> missingLocations) {
        Objects.requireNonNull(missingLocations, "missingLocations must not be null");
        List<String> normalizedLocations = missingLocations.stream()
                .map(ExtractionResult::normalizedLocation)
                .toList();
        if (normalizedLocations.isEmpty() || new LinkedHashSet<>(normalizedLocations).size() != normalizedLocations.size()) {
            throw new IllegalArgumentException("partial extraction requires unique missing locations");
        }
        return new ExtractionResult(
                ExtractionStatus.PARTIAL, normalizedContent(content), normalizedLocations, null, false);
    }

    public static ExtractionResult failed(ExtractionFailure failure) {
        return new ExtractionResult(
                ExtractionStatus.FAILED, null, List.of(), Objects.requireNonNull(failure, "failure must not be null"), false);
    }

    public ExtractionResult confirmPartial() {
        if (status != ExtractionStatus.PARTIAL || confirmedByPlayer) {
            throw new IllegalStateException("only an unconfirmed partial extraction can be confirmed");
        }
        return new ExtractionResult(status, content, missingLocations, null, true);
    }

    public ExtractionStatus status() {
        return status;
    }

    public Optional<String> content() {
        return Optional.ofNullable(content);
    }

    public List<String> missingLocations() {
        return missingLocations;
    }

    public Optional<ExtractionFailure> failure() {
        return Optional.ofNullable(failure);
    }

    public boolean confirmedByPlayer() {
        return confirmedByPlayer;
    }

    private void validate() {
        if (status == ExtractionStatus.SUCCESS
                && (content == null || !missingLocations.isEmpty() || failure != null || confirmedByPlayer)) {
            throw new IllegalArgumentException("successful extraction fields are inconsistent");
        }
        if (status == ExtractionStatus.PARTIAL
                && (content == null || missingLocations.isEmpty() || failure != null)) {
            throw new IllegalArgumentException("partial extraction fields are inconsistent");
        }
        if (status == ExtractionStatus.FAILED
                && (content != null || !missingLocations.isEmpty() || failure == null || confirmedByPlayer)) {
            throw new IllegalArgumentException("failed extraction fields are inconsistent");
        }
    }

    private static String normalizedContent(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("extracted content must not be blank");
        }
        return value;
    }

    private static String normalizedLocation(String value) {
        if (value == null || value.isBlank() || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("missing location must not be blank or contain control characters");
        }
        return value.trim();
    }
}
