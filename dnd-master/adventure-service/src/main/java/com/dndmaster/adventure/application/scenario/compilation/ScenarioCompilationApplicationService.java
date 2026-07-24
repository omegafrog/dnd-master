package com.dndmaster.adventure.application.scenario.compilation;

import com.dndmaster.adventure.application.scenario.ScenarioBundleRepository;
import com.dndmaster.adventure.domain.scenario.OwnerPlayerId;
import com.dndmaster.adventure.domain.scenario.ResolutionOverride;
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
    private final ScenarioCompilationProcessManager processManager;
    private final ScenarioCompilationRepository compilationRepository;
    private final ScenarioSourceExcerptPort excerptPort;
    private final ResolutionOverrideRepository overrideRepository;

    public ScenarioCompilationApplicationService(
            ScenarioBundleRepository bundleRepository,
            ScenarioPackageCompilationService compiler,
            ScenarioPackageRepository packageRepository,
            ScenarioCompilationProcessManager processManager,
            ScenarioCompilationRepository compilationRepository,
            ScenarioSourceExcerptPort excerptPort) {
        this(bundleRepository, compiler, packageRepository, processManager, compilationRepository, excerptPort,
                new NoopResolutionOverrideRepository());
    }

    public ScenarioCompilationApplicationService(
            ScenarioBundleRepository bundleRepository,
            ScenarioPackageCompilationService compiler,
            ScenarioPackageRepository packageRepository,
            ScenarioCompilationProcessManager processManager,
            ScenarioCompilationRepository compilationRepository,
            ScenarioSourceExcerptPort excerptPort,
            ResolutionOverrideRepository overrideRepository) {
        this.bundleRepository = Objects.requireNonNull(bundleRepository, "bundle repository must not be null");
        this.compiler = Objects.requireNonNull(compiler, "compiler must not be null");
        this.packageRepository = Objects.requireNonNull(packageRepository, "package repository must not be null");
        this.processManager = Objects.requireNonNull(processManager, "process manager must not be null");
        this.compilationRepository = Objects.requireNonNull(compilationRepository, "compilation repository must not be null");
        this.excerptPort = Objects.requireNonNull(excerptPort, "excerpt port must not be null");
        this.overrideRepository = Objects.requireNonNull(overrideRepository, "override repository must not be null");
    }

    public ScenarioPackage compile(
            ScenarioBundleId bundleId, OwnerPlayerId owner, List<ResolutionCandidate> candidates) {
        ScenarioSourceBundle bundle = bundleRepository.findById(bundleId)
                .orElseThrow(ScenarioBundleNotFoundException::new);
        bundle.authorize(owner);
        return compiler.compile(bundle, candidates, excerptPort.load(bundle), overrideRepository.findByBundleId(bundleId));
    }

    public ScenarioPackage compile(
            ScenarioBundleId bundleId, OwnerPlayerId owner,
            List<ResolutionCandidate> candidates, List<ResolutionOverride> overrides) {
        ScenarioSourceBundle bundle = bundleRepository.findById(bundleId)
                .orElseThrow(ScenarioBundleNotFoundException::new);
        bundle.authorize(owner);
        if (overrides != null && !overrides.isEmpty()) {
            overrideRepository.saveAll(overrides);
        }
        return compiler.compile(bundle, candidates, excerptPort.load(bundle), overrideRepository.findByBundleId(bundleId));
    }

    public ScenarioPackage compile(ScenarioBundleId bundleId, OwnerPlayerId owner) {
        ScenarioSourceBundle bundle = bundleRepository.findById(bundleId)
                .orElseThrow(ScenarioBundleNotFoundException::new);
        bundle.authorize(owner);
        return compiler.compile(bundle, List.of(), excerptPort.load(bundle), overrideRepository.findByBundleId(bundleId));
    }

    public ScenarioPackage read(UUID packageId, OwnerPlayerId owner) {
        ScenarioPackage scenarioPackage = packageRepository.findById(packageId)
                .orElseThrow(ScenarioBundleNotFoundException::new);
        ScenarioSourceBundle bundle = bundleRepository.findById(scenarioPackage.bundleId())
                .orElseThrow(ScenarioBundleNotFoundException::new);
        bundle.authorize(owner);
        return scenarioPackage;
    }

    public com.dndmaster.adventure.domain.scenario.ScenarioCompilation start(
            ScenarioBundleId bundleId, OwnerPlayerId owner, String inputFingerprint) {
        ScenarioSourceBundle bundle = bundleRepository.findById(bundleId)
                .orElseThrow(ScenarioBundleNotFoundException::new);
        bundle.authorize(owner);
        return processManager.start(bundleId, bundle.currentRevision().revision(), inputFingerprint);
    }

    public com.dndmaster.adventure.domain.scenario.ScenarioCompilation readCompilation(
            UUID compilationId, OwnerPlayerId owner) {
        var compilation = compilationRepository.findById(compilationId)
                .orElseThrow(ScenarioBundleNotFoundException::new);
        ScenarioSourceBundle bundle = bundleRepository.findById(compilation.bundleId())
                .orElseThrow(ScenarioBundleNotFoundException::new);
        bundle.authorize(owner);
        return compilation;
    }
}
