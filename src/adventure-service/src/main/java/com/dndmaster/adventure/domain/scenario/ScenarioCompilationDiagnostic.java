package com.dndmaster.adventure.domain.scenario;

import java.util.Objects;

/** A player-safe explanation of why a compilation is ready, blocked, or needs attention. */
public record ScenarioCompilationDiagnostic(String code, Severity severity, String message, String rootCauseClass) {
    public ScenarioCompilationDiagnostic(String code, Severity severity, String message) {
        this(code, severity, message, null);
    }

    public ScenarioCompilationDiagnostic {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("diagnostic code is required");
        severity = Objects.requireNonNull(severity, "diagnostic severity is required");
        if (message == null || message.isBlank()) throw new IllegalArgumentException("diagnostic message is required");
    }

    public enum Severity { INFO, WARNING, BLOCKING }

    public static ScenarioCompilationDiagnostic blocking(String code, String message) {
        return new ScenarioCompilationDiagnostic(code, Severity.BLOCKING, message);
    }

    public static ScenarioCompilationDiagnostic blocking(String code, String message, String rootCauseClass) {
        return new ScenarioCompilationDiagnostic(code, Severity.BLOCKING, message, rootCauseClass);
    }

    public static ScenarioCompilationDiagnostic warning(String code, String message) {
        return new ScenarioCompilationDiagnostic(code, Severity.WARNING, message);
    }

    public static ScenarioCompilationDiagnostic warning(String code, String message, String rootCauseClass) {
        return new ScenarioCompilationDiagnostic(code, Severity.WARNING, message, rootCauseClass);
    }
}
