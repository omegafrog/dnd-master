package com.dndmaster.adventure.domain.scenario;

import java.util.List;
import java.util.Objects;

/** Package report. Legacy status remains readable; outcome is the canonical policy result. */
public final class ScenarioCompilationReport {
    private final ResolutionStatus status;
    private final List<String> warnings;
    private final CompilationOutcome outcome;

    public ScenarioCompilationReport(ResolutionStatus status, List<String> warnings) {
        this(status, warnings, legacyOutcome(status));
    }

    public ScenarioCompilationReport(ResolutionStatus status, List<String> warnings, CompilationOutcome outcome) {
        this.status = Objects.requireNonNull(status, "report status must not be null");
        this.warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings must not be null"));
        this.outcome = outcome == null ? legacyOutcome(status) : outcome;
    }

    public ResolutionStatus status() { return status; }
    public List<String> warnings() { return warnings; }

    /** New package outcome projection; status remains for legacy readers. */
    public CompilationOutcome outcome() {
        return outcome;
    }

    private static CompilationOutcome legacyOutcome(ResolutionStatus status) {
        return switch (Objects.requireNonNull(status, "report status must not be null")) {
            case COMPLETE -> CompilationOutcome.COMPLETE;
            case PARTIAL -> CompilationOutcome.COMPLETE_WITH_WARNINGS;
            case INVALID -> CompilationOutcome.FAILED;
        };
    }
}
