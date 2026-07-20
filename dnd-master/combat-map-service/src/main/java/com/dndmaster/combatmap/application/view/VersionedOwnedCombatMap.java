package com.dndmaster.combatmap.application.view;
import com.dndmaster.combatmap.domain.CombatMap; import java.util.Objects;
public record VersionedOwnedCombatMap(CombatMap map,MapOwnerId owner,long version){public VersionedOwnedCombatMap{Objects.requireNonNull(map);Objects.requireNonNull(owner);if(version<0)throw new IllegalArgumentException("version negative");}}
