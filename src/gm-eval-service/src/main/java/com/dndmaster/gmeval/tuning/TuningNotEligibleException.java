package com.dndmaster.gmeval.tuning;

public final class TuningNotEligibleException extends IllegalStateException {
    private final TuningEligibility eligibility;

    public TuningNotEligibleException(TuningEligibility eligibility) {
        super("tuning proposal is not eligible: " + eligibility.proposalId());
        this.eligibility = eligibility;
    }

    public TuningEligibility eligibility() { return eligibility; }
}
