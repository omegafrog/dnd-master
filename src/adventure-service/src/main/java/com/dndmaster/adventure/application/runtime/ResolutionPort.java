package com.dndmaster.adventure.application.runtime;

public interface ResolutionPort {
    ResolutionResult resolve(CheckSelection selection, int systemRoll);
}
