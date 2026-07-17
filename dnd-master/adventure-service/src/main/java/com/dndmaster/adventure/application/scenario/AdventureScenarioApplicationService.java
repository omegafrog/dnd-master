package com.dndmaster.adventure.application.scenario;

import com.dndmaster.adventure.domain.scenario.AdventureScenario;
import com.dndmaster.adventure.domain.scenario.RequestingPlayerId;
import com.dndmaster.adventure.domain.scenario.ScenarioId;
import java.util.Objects;

public final class AdventureScenarioApplicationService {
    private final AdventureScenarioRepository repository;
    private final ScenarioStoragePort storagePort;
    private final ScenarioPreparationPort preparationPort;

    public AdventureScenarioApplicationService(
            AdventureScenarioRepository repository,
            ScenarioStoragePort storagePort,
            ScenarioPreparationPort preparationPort) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.storagePort = Objects.requireNonNull(storagePort, "storage port must not be null");
        this.preparationPort = Objects.requireNonNull(preparationPort, "preparation port must not be null");
    }

    public AdventureScenario uploadScenario(ScenarioUpload upload) {
        Objects.requireNonNull(upload, "upload must not be null");
        var source = storagePort.store(upload);
        var scenario = AdventureScenario.recordUpload(ScenarioId.generate(), upload.ownerPlayerId(), source);
        repository.save(scenario);
        return scenario;
    }

    public AdventureScenario prepareScenario(ScenarioId scenarioId, RequestingPlayerId requestingPlayerId) {
        AdventureScenario scenario = loadOwned(scenarioId, requestingPlayerId);
        try {
            preparationPort.prepare(scenario.source());
        } catch (RuntimeException exception) {
            scenario.recordPreparationFailure("preparation port failed");
            repository.save(scenario);
            throw new ScenarioPreparationFailedException(exception);
        }
        scenario.recordPreparationSuccess();
        repository.save(scenario);
        return scenario;
    }

    public AdventureScenario accessScenario(ScenarioId scenarioId, RequestingPlayerId requestingPlayerId) {
        AdventureScenario scenario = loadOwned(scenarioId, requestingPlayerId);
        if (!scenario.isUsableByAiGameMaster()) {
            throw new ScenarioNotReadyException();
        }
        return scenario;
    }

    private AdventureScenario loadOwned(ScenarioId scenarioId, RequestingPlayerId requestingPlayerId) {
        Objects.requireNonNull(scenarioId, "scenario id must not be null");
        AdventureScenario scenario = repository.findById(scenarioId).orElseThrow(ScenarioNotFoundException::new);
        scenario.authorizeAccess(requestingPlayerId);
        return scenario;
    }
}
