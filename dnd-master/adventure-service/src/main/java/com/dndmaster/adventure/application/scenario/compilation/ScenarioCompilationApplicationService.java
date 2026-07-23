package com.dndmaster.adventure.application.scenario.compilation;

import com.dndmaster.adventure.application.scenario.ScenarioBundleRepository;
import com.dndmaster.adventure.domain.scenario.OwnerPlayerId;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleId;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleNotFoundException;
import com.dndmaster.adventure.domain.scenario.ScenarioPackage;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceBundle;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class ScenarioCompilationApplicationService {
    private final ScenarioBundleRepository bundleRepository;
    private final ScenarioPackageCompilationService compiler;
    private final ScenarioPackageRepository packageRepository;

    public ScenarioCompilationApplicationService(
            ScenarioBundleRepository bundleRepository,
            ScenarioPackageCompilationService compiler,
            ScenarioPackageRepository packageRepository) {
        this.bundleRepository = Objects.requireNonNull(bundleRepository, "bundle repository must not be null");
        this.compiler = Objects.requireNonNull(compiler, "compiler must not be null");
        this.packageRepository = Objects.requireNonNull(packageRepository, "package repository must not be null");
    }

    public ScenarioPackage compile(
            ScenarioBundleId bundleId, OwnerPlayerId owner, List<ResolutionCandidate> candidates) {
        ScenarioSourceBundle bundle = bundleRepository.findById(bundleId)
                .orElseThrow(ScenarioBundleNotFoundException::new);
        bundle.authorize(owner);
        return compiler.compile(bundle, candidates);
    }

    public ScenarioPackage compile(ScenarioBundleId bundleId, OwnerPlayerId owner) {
        ScenarioSourceBundle bundle = bundleRepository.findById(bundleId)
                .orElseThrow(ScenarioBundleNotFoundException::new);
        bundle.authorize(owner);
        return compiler.compile(bundle, List.of());
    }

    public ScenarioPackage read(UUID packageId, OwnerPlayerId owner) {
        ScenarioPackage scenarioPackage = packageRepository.findById(packageId)
                .orElseThrow(ScenarioBundleNotFoundException::new);
        ScenarioSourceBundle bundle = bundleRepository.findById(scenarioPackage.bundleId())
                .orElseThrow(ScenarioBundleNotFoundException::new);
        bundle.authorize(owner);
        return scenarioPackage;
    }
}
