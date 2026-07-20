package com.dndmaster.combatmap.application.movement;
import com.dndmaster.combatmap.domain.*; import java.util.Optional;
public interface CombatMapRepository { Optional<CombatMap> findById(MapId id); void save(CombatMap map); }
