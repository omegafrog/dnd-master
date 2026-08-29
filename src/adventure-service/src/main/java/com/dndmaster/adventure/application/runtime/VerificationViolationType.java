package com.dndmaster.adventure.application.runtime;

public enum VerificationViolationType {
    SECRET_LEAK, UNSUPPORTED_FACT, RULE_MISMATCH, PLAYER_AGENCY_VIOLATION,
    NPC_KNOWLEDGE_VIOLATION, TURNPLAN_DEVIATION, STATE_CONTRADICTION
}
