package com.dndmaster.adventure.domain.inquiry;

public final class UndisclosedCandidateSelectionException extends RuntimeException {
    public UndisclosedCandidateSelectionException() {
        super("the selected rule was not disclosed as a candidate");
    }
}
