package com.dndmaster.gmeval.optimization;

/** Safety metrics. Higher values mean more violations and are never quality wins. */
public enum HardMetric {
    RULE_VIOLATION,
    SECRET_LEAK,
    AGENCY_VIOLATION,
    SCHEMA_FAILURE
}
