package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.adventure.AdventureContext;

@FunctionalInterface
public interface AuthoritativeStateMutationPort {
    AdventureContext apply(AdventureContext current, AuthoritativeResolution resolution);
}
