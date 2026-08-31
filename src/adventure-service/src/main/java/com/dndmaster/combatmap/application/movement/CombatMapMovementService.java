package com.dndmaster.combatmap.application.movement;
import com.dndmaster.combatmap.domain.*; import java.util.Objects;
public final class CombatMapMovementService {
    private final CombatMapRepository repository; private final AppliedEditionMovementPort movementPort;
    public CombatMapMovementService(CombatMapRepository repository, AppliedEditionMovementPort movementPort){this.repository=Objects.requireNonNull(repository);this.movementPort=Objects.requireNonNull(movementPort);}
    public CombatMap movePlayerToken(MovePlayerTokenCommand command){
        Objects.requireNonNull(command);
        CombatMap replay = repository.findByCommandId(command.commandId()).orElse(null);
        if (replay != null) {
            if (!command.fingerprint().equals(replay.operationFingerprint())) throw new IllegalStateException("combat map command id reused with different payload");
            return replay;
        }
        CombatMap map=repository.findById(command.mapId()).orElseThrow(()->new CombatMapMovementDeniedException("map not found"));
        if(map.version()!=command.expectedVersion()) throw new IllegalStateException("combat map version does not match");
        int maximum=movementPort.maximumMovement(map.ruleSetId(),command.appliedEdition());
        map.movePlayerToken(command.playerId(),command.tokenId(),command.path(),maximum);
        map.refreshVisibility(map.visibilitySnapshot() == null ? 0 : map.visibilitySnapshot().ruleTurn());
        repository.save(map, command.expectedVersion()+1, command.commandId(), command.fingerprint());
        map.markPersisted(command.expectedVersion()+1, command.commandId(), command.fingerprint());
        return map;
    }
}
