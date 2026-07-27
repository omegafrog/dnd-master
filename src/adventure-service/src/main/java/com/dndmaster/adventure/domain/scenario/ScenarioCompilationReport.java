package com.dndmaster.adventure.domain.scenario;

import java.util.List;
import java.util.Objects;

public record ScenarioCompilationReport(ResolutionStatus status, List<String> warnings) {
    public ScenarioCompilationReport {
        status = Objects.requireNonNull(status, "report status must not be null");
        warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings must not be null"));
    }
}
