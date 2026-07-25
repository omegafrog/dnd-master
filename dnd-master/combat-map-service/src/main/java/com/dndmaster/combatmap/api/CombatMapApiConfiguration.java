package com.dndmaster.combatmap.api;

import com.dndmaster.combatmap.application.movement.AppliedEditionMovementPort;
import com.dndmaster.combatmap.application.movement.CombatMapMovementService;
import com.dndmaster.combatmap.application.movement.CombatMapRepository;
import com.dndmaster.combatmap.application.view.*;
import com.dndmaster.combatmap.domain.*;
import com.dndmaster.combatmap.infrastructure.persistence.PostgresCombatMapViewStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.List;
import java.util.Set;

@Configuration(proxyBeanMethods = false)
public class CombatMapApiConfiguration {

    @Bean
    CombatMapViewStore combatMapViewStore(DataSource dataSource) {
        return new PostgresCombatMapViewStore(dataSource);
    }

    @Bean
    CombatMapRepository combatMapRepository(DataSource dataSource) {
        return new CombatMapRepository() {
            private final PostgresCombatMapViewStore store = new PostgresCombatMapViewStore(dataSource);

            @Override
            public java.util.Optional<CombatMap> findById(MapId id) {
                return store.find(id).map(VersionedOwnedCombatMap::map);
            }

            @Override
            public java.util.Optional<CombatMap> findByCommandId(java.util.UUID commandId) {
                return store.findByCommandId(commandId).map(VersionedOwnedCombatMap::map);
            }

            @Override
            public void save(CombatMap map) {
                if (map.ownerPlayerId() == null) {
                    throw new IllegalStateException("combat map owner is required for persistence");
                }
                store.update(
                        new com.dndmaster.combatmap.application.view.MapOwnerId(map.ownerPlayerId().value()),
                        map,
                        map.version(),
                        map.version() + 1,
                        map.operationKey(),
                        map.operationFingerprint());
            }

            @Override
            public void save(CombatMap map, long persistedVersion, java.util.UUID operationKey, String operationFingerprint) {
                if (map.ownerPlayerId() == null) {
                    throw new IllegalStateException("combat map owner is required for persistence");
                }
                store.update(
                        new com.dndmaster.combatmap.application.view.MapOwnerId(map.ownerPlayerId().value()),
                        map,
                        map.version(),
                        persistedVersion,
                        operationKey,
                        operationFingerprint);
            }
        };
    }

    @Bean
    CombatMapViewService combatMapViewService(
            CombatMapViewStore store, MapFilePreparationPort filePort, AiMapGenerationPort aiPort) {
        return new CombatMapViewService(store, filePort, aiPort);
    }

    @Bean
    CombatMapMovementService combatMapMovementService(
            CombatMapRepository repository, AppliedEditionMovementPort movementPort) {
        return new CombatMapMovementService(repository, movementPort);
    }

    @Bean
    MapFilePreparationPort mapFilePreparationPort() {
        return source -> new PreparedMapData(
                new GridSpec(20, 20, 30, 5),
                List.of(),
                Set.of(),
                List.of());
    }

    @Bean
    AiMapGenerationPort aiMapGenerationPort() {
        return scenarioDescription -> new PreparedMapData(
                new GridSpec(20, 20, 30, 5),
                List.of(),
                Set.of(),
                List.of());
    }

    @Bean
    AppliedEditionMovementPort appliedEditionMovementPort() {
        return (ruleSetId, appliedEdition) -> 30;
    }

    @Bean
    CombatMapController combatMapController(
            CombatMapViewService mapViewService, CombatMapMovementService movementService) {
        return new CombatMapController(mapViewService, movementService);
    }
}
