package com.dndmaster.adventure.application.progress;

import com.dndmaster.adventure.domain.adventure.Adventure;

public interface AdventureReadinessPort {
    AdventureReadiness check(Adventure adventure);
}
