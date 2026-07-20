package com.dndmaster.combatmap.application.movement;
import com.dndmaster.combatmap.domain.*; import java.util.Objects;
public final class CombatMapMovementService {
    private final CombatMapRepository repository; private final AppliedEditionMovementPort movementPort;
    public CombatMapMovementService(CombatMapRepository repository, AppliedEditionMovementPort movementPort){this.repository=Objects.requireNonNull(repository);this.movementPort=Objects.requireNonNull(movementPort);}
    public CombatMap movePlayerToken(MovePlayerTokenCommand command){Objects.requireNonNull(command); CombatMap map=repository.findById(command.mapId()).orElseThrow(()->new CombatMapMovementDeniedException("map not found")); int maximum=movementPort.maximumMovement(map.ruleSetId(),command.appliedEdition()); map.movePlayerToken(command.playerId(),command.tokenId(),command.path(),maximum);repository.save(map);return map;}
}
