package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.RuntimeBinding;
import java.util.List;
import java.util.Optional;

public interface RuntimeBindingRepository {
    Optional<RuntimeBinding> findCurrentByAdventureId(AdventureId adventureId);
    List<RuntimeBinding> findAllByAdventureId(AdventureId adventureId);
    void save(RuntimeBinding binding);
}
