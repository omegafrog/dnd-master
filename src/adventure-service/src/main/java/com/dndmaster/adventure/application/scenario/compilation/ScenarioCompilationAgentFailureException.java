package com.dndmaster.adventure.application.scenario.compilation;

import java.util.Objects;

/** Safe, structured failure returned by the scenario compilation AI boundary. */
public final class ScenarioCompilationAgentFailureException extends RuntimeException {
    private final int httpStatus;
    private final String code;
    private final String correlationId;
    private final String rootCauseClass;
    private final boolean retryable;

    public ScenarioCompilationAgentFailureException(
            int httpStatus, String code, String correlationId, String rootCauseClass, boolean retryable) {
        super("scenario compilation agent failed: HTTP " + httpStatus + " code=" + safeCode(code)
                + " rootCauseClass=" + safeClassName(rootCauseClass)
                + " correlationId=" + safeCorrelation(correlationId));
        this.httpStatus = httpStatus;
        this.code = safeCode(code);
        this.correlationId = safeCorrelation(correlationId);
        this.rootCauseClass = safeClassName(rootCauseClass);
        this.retryable = retryable;
    }

    private static String safeCode(String value) {
        Objects.requireNonNull(value, "failure code must not be null");
        if (!value.matches("[A-Z0-9_]{1,80}")) throw new IllegalArgumentException("invalid failure code");
        return value;
    }

    private static String safeCorrelation(String value) {
        Objects.requireNonNull(value, "correlation id must not be null");
        if (!value.matches("[A-Za-z0-9:_-]{1,120}")) throw new IllegalArgumentException("invalid correlation id");
        return value;
    }

    private static String safeClassName(String value) {
        Objects.requireNonNull(value, "root cause class must not be null");
        if (!value.matches("[A-Z][A-Za-z0-9_$]{0,119}")) throw new IllegalArgumentException("invalid root cause class");
        return value;
    }

    public int httpStatus() { return httpStatus; }
    public String code() { return code; }
    public String correlationId() { return correlationId; }
    public String rootCauseClass() { return rootCauseClass; }
    public boolean retryable() { return retryable; }
}
