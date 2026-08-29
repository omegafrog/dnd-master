package com.dndmaster.adventure.application.runtime;

/** Admission gate for the separate style corpus. */
public final class ExemplarAdmissionPolicy {
    public boolean admit(ExemplarAdmissionCandidate candidate) {
        return candidate != null && candidate.verification().status() == VerificationStatus.PASS
                && !candidate.containsRuntimePollution() && !candidate.sessionScoped()
                && candidate.exemplar().generic();
    }
}
