package com.dndmaster.combatmap.application.view;
import com.dndmaster.combatmap.domain.*; import java.util.Optional;
public interface CombatMapViewStore{void insert(MapOwnerId owner,CombatMap map);Optional<VersionedOwnedCombatMap> find(MapId id);long update(MapOwnerId owner,CombatMap map,long expectedVersion);}
