package com.dndmaster.adventure.application.scenario.compilation;

import com.dndmaster.adventure.domain.scenario.CandidateRecoverability;
import java.util.Objects;

public final class CandidateRepairPolicy {
    public static final int MAX_REPAIR_ATTEMPTS = 1;

    public boolean mayRepair(CandidateRecoverability recoverability, int repairAttempts) {
        Objects.requireNonNull(recoverability, "recoverability must not be null");
        if (repairAttempts < 0) throw new IllegalArgumentException("repair attempts must not be negative");
        return repairAttempts < MAX_REPAIR_ATTEMPTS
                && (recoverability == CandidateRecoverability.REPAIRABLE
                        || recoverability == CandidateRecoverability.MAYBE_REPAIRABLE);
    }

    public boolean mayRepair(String validationCode, int repairAttempts) {
        if (validationCode == null || validationCode.isBlank()) return false;
        CandidateRecoverability recoverability = switch (validationCode) {
            case "DICE_EXPRESSION_INVALID", "RECHARGE_RANGE_INVALID", "DC_MISSING", "DC_RESOLUTION_MISSING", "SOURCE_EXCERPT_UNAVAILABLE" -> CandidateRecoverability.REPAIRABLE;
            case "SOURCE_QUOTE_UNVERIFIED" -> CandidateRecoverability.MAYBE_REPAIRABLE;
            default -> CandidateRecoverability.NON_REPAIRABLE;
        };
        return mayRepair(recoverability, repairAttempts);
    }
}
