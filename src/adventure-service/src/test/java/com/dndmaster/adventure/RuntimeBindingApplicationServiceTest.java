package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.application.knowledge.KnowledgeDocumentLookupPort;
import com.dndmaster.adventure.application.knowledge.KnowledgeDocumentStatus;
import com.dndmaster.adventure.application.runtime.InitialSourceContextProposalPort;
import com.dndmaster.adventure.application.runtime.RuntimeBindingApplicationService;
import com.dndmaster.adventure.application.runtime.RuntimeBindingRepository;
import com.dndmaster.adventure.application.saved.AdventureRepository;
import com.dndmaster.adventure.application.scenario.ScenarioBundleRepository;
import com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageRepository;
import com.dndmaster.adventure.domain.adventure.ActiveSourceContext;
import com.dndmaster.adventure.domain.adventure.Adventure;
import com.dndmaster.adventure.domain.adventure.AdventureContext;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.CharacterSheetId;
import com.dndmaster.adventure.domain.adventure.InitialSourceContextCandidate;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.adventure.PlayabilityStatus;
import com.dndmaster.adventure.domain.adventure.RuntimeBinding;
import com.dndmaster.adventure.domain.adventure.RuntimeReadinessStatus;
import com.dndmaster.adventure.domain.adventure.ScenarioId;
import com.dndmaster.adventure.domain.adventure.SessionId;
import com.dndmaster.adventure.domain.adventure.RuleSetId;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.domain.scenario.ResolutionKind;
import com.dndmaster.adventure.domain.scenario.ResolutionStatus;
import com.dndmaster.adventure.domain.scenario.ResolutionVisibility;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentRole;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentSelection;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleId;
import com.dndmaster.adventure.domain.scenario.ScenarioCompilationReport;
import com.dndmaster.adventure.domain.scenario.ScenarioPackage;
import com.dndmaster.adventure.domain.scenario.ScenarioResolutionDetail;
import com.dndmaster.adventure.domain.scenario.ScenarioResolutionUnit;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceBundle;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceBundleRevision;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceReference;
import java.util.*;
import org.junit.jupiter.api.Test;

class RuntimeBindingApplicationServiceTest {
    @Test
    void reports_indexing_pending_without_starting_runtime() {
        ScenarioBundleId bundleId = ScenarioBundleId.generate();
        OwnerPlayerId owner = new OwnerPlayerId(UUID.randomUUID());
        Adventure adventure = adventure(owner);
        KnowledgeDocumentId rulebookId = new KnowledgeDocumentId(UUID.randomUUID());
        KnowledgeDocumentId storyId = new KnowledgeDocumentId(UUID.randomUUID());
        ScenarioPackage scenarioPackage = scenarioPackage(bundleId, rulebookId, storyId, "page:1:span:1");
        InMemoryBindingRepository bindings = new InMemoryBindingRepository();
        RuntimeBinding binding = service(new InMemoryAdventureRepository(adventure), new InMemoryBundleRepository(bundleId, owner),
                new InMemoryPackageRepository(scenarioPackage), bindings, rulebookId, KnowledgeDocumentStatus.UPLOADED)
                .bind(new RuntimeBindingApplicationService.BindRuntimeBindingCommand(adventure.id(), owner,
                        scenarioPackage.packageId(), List.of(rulebookId.value()), "ollama", List.of("search")));

        assertEquals(RuntimeReadinessStatus.INDEXING_PENDING, binding.readiness().status());
        assertEquals(binding.bindingVersion(), binding.readiness().bindingVersion());
        assertEquals(true, binding.readiness().retryable());
    }

    @Test
    void reports_confirmed_partial_indexing_as_supported_degraded() {
        ScenarioBundleId bundleId = ScenarioBundleId.generate();
        OwnerPlayerId owner = new OwnerPlayerId(UUID.randomUUID());
        Adventure adventure = adventure(owner);
        KnowledgeDocumentId rulebookId = new KnowledgeDocumentId(UUID.randomUUID());
        KnowledgeDocumentId storyId = new KnowledgeDocumentId(UUID.randomUUID());
        ScenarioPackage scenarioPackage = scenarioPackage(bundleId, rulebookId, storyId, "page:1:span:1");

        RuntimeBinding binding = service(new InMemoryAdventureRepository(adventure), new InMemoryBundleRepository(bundleId, owner),
                new InMemoryPackageRepository(scenarioPackage), new InMemoryBindingRepository(), rulebookId,
                KnowledgeDocumentStatus.PARTIAL_CONFIRMED)
                .bind(new RuntimeBindingApplicationService.BindRuntimeBindingCommand(adventure.id(), owner,
                        scenarioPackage.packageId(), List.of(rulebookId.value()), "ollama", List.of("search")));

        assertEquals(RuntimeReadinessStatus.SUPPORTED_DEGRADED, binding.readiness().status());
        assertEquals(true, binding.readiness().ready());
    }

    @Test
    void blocks_unsupported_provider_capability() {
        ScenarioBundleId bundleId = ScenarioBundleId.generate();
        OwnerPlayerId owner = new OwnerPlayerId(UUID.randomUUID());
        Adventure adventure = adventure(owner);
        KnowledgeDocumentId rulebookId = new KnowledgeDocumentId(UUID.randomUUID());
        ScenarioPackage scenarioPackage = scenarioPackage(bundleId, rulebookId, new KnowledgeDocumentId(UUID.randomUUID()), "page:1:span:1");

        RuntimeBinding binding = service(new InMemoryAdventureRepository(adventure), new InMemoryBundleRepository(bundleId, owner),
                new InMemoryPackageRepository(scenarioPackage), new InMemoryBindingRepository(), rulebookId)
                .bind(new RuntimeBindingApplicationService.BindRuntimeBindingCommand(adventure.id(), owner,
                        scenarioPackage.packageId(), List.of(rulebookId.value()), "unsupported-provider", List.of("search")));

        assertEquals(RuntimeReadinessStatus.BLOCKED, binding.readiness().status());
        assertTrue(binding.readiness().blockers().stream().anyMatch(reason -> reason.contains("unsupported-provider")));
    }

    @Test
    void bindsPlayablePackageAndAutoSelectsSingleContext() {
        ScenarioBundleId bundleId = ScenarioBundleId.generate();
        OwnerPlayerId owner = new OwnerPlayerId(UUID.randomUUID());
        Adventure adventure = adventure(owner);
        KnowledgeDocumentId rulebookId = new KnowledgeDocumentId(UUID.randomUUID());
        KnowledgeDocumentId storyId = new KnowledgeDocumentId(UUID.randomUUID());
        ScenarioPackage scenarioPackage = scenarioPackage(bundleId, rulebookId, storyId, "page:1:span:1");

        InMemoryAdventureRepository adventures = new InMemoryAdventureRepository(adventure);
        InMemoryBundleRepository bundles = new InMemoryBundleRepository(bundleId, owner);
        InMemoryPackageRepository packages = new InMemoryPackageRepository(scenarioPackage);
        InMemoryBindingRepository bindings = new InMemoryBindingRepository();
        RuntimeBindingApplicationService service = service(adventures, bundles, packages, bindings, rulebookId);

        RuntimeBinding binding = service.bind(new RuntimeBindingApplicationService.BindRuntimeBindingCommand(
                adventure.id(), owner, scenarioPackage.packageId(), List.of(rulebookId.value()),
                "ollama", List.of("search", "move")));

        assertEquals(PlayabilityStatus.PLAYABLE, binding.playabilityReport().status());
        assertNotNull(binding.activeSourceContext());
        assertEquals("page:1:span:1", binding.activeSourceContext().locator());
        assertEquals(1, binding.bindingVersion());
        assertEquals(binding, bindings.current);
        assertEquals(RuntimeReadinessStatus.INDEXED_READY, binding.readiness().status());
        assertEquals(binding.bindingVersion(), binding.readiness().bindingVersion());
    }

    @Test
    void blocksAmbiguousContextUntilOwnerChoosesOne() {
        ScenarioBundleId bundleId = ScenarioBundleId.generate();
        OwnerPlayerId owner = new OwnerPlayerId(UUID.randomUUID());
        Adventure adventure = adventure(owner);
        KnowledgeDocumentId rulebookId = new KnowledgeDocumentId(UUID.randomUUID());
        KnowledgeDocumentId storyId = new KnowledgeDocumentId(UUID.randomUUID());
        ScenarioPackage scenarioPackage = scenarioPackage(bundleId, rulebookId, storyId, "page:1:span:1", "page:1:span:9");

        InMemoryAdventureRepository adventures = new InMemoryAdventureRepository(adventure);
        InMemoryBundleRepository bundles = new InMemoryBundleRepository(bundleId, owner);
        InMemoryPackageRepository packages = new InMemoryPackageRepository(scenarioPackage);
        InMemoryBindingRepository bindings = new InMemoryBindingRepository();
        RuntimeBindingApplicationService service = service(adventures, bundles, packages, bindings, rulebookId);

        RuntimeBinding blocked = service.bind(new RuntimeBindingApplicationService.BindRuntimeBindingCommand(
                adventure.id(), owner, scenarioPackage.packageId(), List.of(rulebookId.value()),
                "ollama", List.of("search")));

        assertEquals(PlayabilityStatus.BLOCKED, blocked.playabilityReport().status());
        assertEquals(2, blocked.playabilityReport().candidates().size());
        assertEquals("initial source context is ambiguous", blocked.playabilityReport().blockers().get(0));

        RuntimeBinding chosen = service.chooseActiveSourceContext(new RuntimeBindingApplicationService.ChooseActiveSourceContextCommand(
                adventure.id(), owner, blocked.bindingVersion(), "page:1:span:9"));

        assertEquals(PlayabilityStatus.PLAYABLE, chosen.playabilityReport().status());
        assertNotNull(chosen.activeSourceContext());
        assertEquals("page:1:span:9", chosen.activeSourceContext().locator());
        assertEquals(RuntimeReadinessStatus.INDEXED_READY, chosen.readiness().status());
    }

    @Test
    void rejectsScenarioPackageSwitchForPartyBoundRuntime() {
        ScenarioBundleId bundleId = ScenarioBundleId.generate();
        OwnerPlayerId owner = new OwnerPlayerId(UUID.randomUUID());
        Adventure adventure = adventure(owner);
        KnowledgeDocumentId rulebookId = new KnowledgeDocumentId(UUID.randomUUID());
        KnowledgeDocumentId storyId = new KnowledgeDocumentId(UUID.randomUUID());
        ScenarioPackage firstPackage = scenarioPackage(bundleId, rulebookId, storyId, "page:1:span:1");
        ScenarioPackage secondPackage = scenarioPackage(bundleId, rulebookId, storyId, "page:2:span:1");

        InMemoryAdventureRepository adventures = new InMemoryAdventureRepository(adventure);
        InMemoryBundleRepository bundles = new InMemoryBundleRepository(bundleId, owner);
        InMemoryPackageRepository packages = new InMemoryPackageRepository(firstPackage, secondPackage);
        InMemoryBindingRepository bindings = new InMemoryBindingRepository();
        RuntimeBindingApplicationService service = service(adventures, bundles, packages, bindings, rulebookId);

        RuntimeBinding initial = service.bind(new RuntimeBindingApplicationService.BindRuntimeBindingCommand(
                adventure.id(), owner, firstPackage.packageId(), List.of(rulebookId.value()),
                "ollama", List.of("search")));
        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> service.switchScenarioPackage(
                new RuntimeBindingApplicationService.SwitchRuntimePackageCommand(
                        adventure.id(), owner, initial.bindingVersion(), secondPackage.packageId())));

        assertEquals(1, initial.bindingVersion());
        assertEquals("started session runtime package is frozen", failure.getMessage());
        assertEquals(1, bindings.current.bindingVersion());
    }

    @Test
    void rejects_direct_rebind_after_session_runtime_has_a_binding() {
        ScenarioBundleId bundleId = ScenarioBundleId.generate();
        OwnerPlayerId owner = new OwnerPlayerId(UUID.randomUUID());
        Adventure adventure = adventure(owner);
        KnowledgeDocumentId rulebookId = new KnowledgeDocumentId(UUID.randomUUID());
        KnowledgeDocumentId storyId = new KnowledgeDocumentId(UUID.randomUUID());
        ScenarioPackage firstPackage = scenarioPackage(bundleId, rulebookId, storyId, "page:1:span:1");

        InMemoryAdventureRepository adventures = new InMemoryAdventureRepository(adventure);
        InMemoryBundleRepository bundles = new InMemoryBundleRepository(bundleId, owner);
        InMemoryPackageRepository packages = new InMemoryPackageRepository(firstPackage);
        InMemoryBindingRepository bindings = new InMemoryBindingRepository();
        RuntimeBindingApplicationService service = service(adventures, bundles, packages, bindings, rulebookId);
        RuntimeBindingApplicationService.BindRuntimeBindingCommand command = new RuntimeBindingApplicationService.BindRuntimeBindingCommand(
                adventure.id(), owner, firstPackage.packageId(), List.of(rulebookId.value()),
                "ollama", List.of("search"));

        service.bind(command);

        assertThrows(IllegalStateException.class, () -> service.bind(command));
    }

    private static RuntimeBindingApplicationService service(
            AdventureRepository adventureRepository,
            ScenarioBundleRepository bundleRepository,
            ScenarioPackageRepository packageRepository,
            RuntimeBindingRepository bindingRepository,
            KnowledgeDocumentId rulebookId) {
        return new RuntimeBindingApplicationService(
                adventureRepository,
                bundleRepository,
                packageRepository,
                bindingRepository,
                (proposalPackage, candidates) -> new InitialSourceContextProposalPort.InitialSourceContextProposalResult(
                        candidates.size() > 1 ? "AMBIGUOUS" : "CLEAR",
                        candidates),
                ownerId -> List.of(new KnowledgeDocumentLookupPort.KnowledgeDocumentRecord(
                        rulebookId, KnowledgeDocumentStatus.INDEXED, "rules.pdf", "RULEBOOK", 1)));
    }

    private static RuntimeBindingApplicationService service(
            AdventureRepository adventureRepository, ScenarioBundleRepository bundleRepository,
            ScenarioPackageRepository packageRepository, RuntimeBindingRepository bindingRepository,
            KnowledgeDocumentId rulebookId, KnowledgeDocumentStatus status) {
        return new RuntimeBindingApplicationService(adventureRepository, bundleRepository, packageRepository, bindingRepository,
                (proposalPackage, candidates) -> new InitialSourceContextProposalPort.InitialSourceContextProposalResult("CLEAR", candidates),
                ownerId -> List.of(new KnowledgeDocumentLookupPort.KnowledgeDocumentRecord(
                        rulebookId, status, "rules.pdf", "RULEBOOK", 1)));
    }

    private static Adventure adventure(OwnerPlayerId owner) {
        return Adventure.create(
                AdventureId.generate(), new SessionId(UUID.randomUUID()), owner, new ScenarioId(UUID.randomUUID()),
                new RuleSetId(UUID.randomUUID()), new CharacterSheetId(UUID.randomUUID()),
                new AdventureContext("start", null, null, null));
    }

    private static ScenarioPackage scenarioPackage(
            ScenarioBundleId bundleId, KnowledgeDocumentId rulebookId, KnowledgeDocumentId storyId, String... locators) {
        List<ScenarioResolutionUnit> units = Arrays.stream(locators)
                .map(locator -> new ScenarioResolutionUnit(
                        ResolutionKind.DICE_ROLL,
                        null,
                        null,
                        "1d6",
                        ResolutionVisibility.GM_REFERENCE,
                        "Roll 1d6.",
                        List.of(new ScenarioSourceReference(storyId, 1, locator)),
                        "fixture",
                        ScenarioResolutionDetail.empty(),
                        ResolutionStatus.COMPLETE,
                        List.of()))
                .toList();
        return ScenarioPackage.publish(
                bundleId, 1, "fingerprint",
                List.of(new ScenarioBundleDocumentSelection(
                        storyId, ScenarioBundleDocumentRole.MAIN_SCENARIO, KnowledgeDocumentStatus.INDEXED,
                        "scenario.pdf", "STORYBOOK", 1)),
                units,
                new ScenarioCompilationReport(ResolutionStatus.COMPLETE, List.of()));
    }

    private static final class InMemoryAdventureRepository implements AdventureRepository {
        private Adventure current;

        private InMemoryAdventureRepository(Adventure current) {
            this.current = current;
        }

        @Override
        public Optional<Adventure> findById(AdventureId adventureId) {
            return current.id().equals(adventureId) ? Optional.of(current) : Optional.empty();
        }

        @Override
        public List<Adventure> findSavedByOwner(OwnerPlayerId ownerPlayerId) {
            return List.of(current);
        }

        @Override
        public void save(Adventure adventure) {
            current = adventure;
        }
    }

    private static final class InMemoryBundleRepository implements ScenarioBundleRepository {
        private final ScenarioSourceBundle bundle;

        private InMemoryBundleRepository(ScenarioBundleId bundleId, OwnerPlayerId owner) {
            this.bundle = ScenarioSourceBundle.create(
                    bundleId,
                    new com.dndmaster.adventure.domain.scenario.OwnerPlayerId(owner.value()),
                    new ScenarioSourceBundleRevision(1, List.of(
                            new ScenarioBundleDocumentSelection(
                                    new KnowledgeDocumentId(UUID.randomUUID()),
                                    ScenarioBundleDocumentRole.MAIN_SCENARIO,
                                    KnowledgeDocumentStatus.INDEXED,
                                    "scenario.pdf",
                                    "STORYBOOK",
                                    1))));
        }

        @Override
        public Optional<ScenarioSourceBundle> findById(ScenarioBundleId id) {
            return bundle.id().equals(id) ? Optional.of(bundle) : Optional.empty();
        }

        @Override
        public void save(ScenarioSourceBundle bundle) {}
    }

    private static final class InMemoryPackageRepository implements ScenarioPackageRepository {
        private final Map<UUID, ScenarioPackage> packages = new HashMap<>();

        private InMemoryPackageRepository(ScenarioPackage... scenarioPackages) {
            for (ScenarioPackage scenarioPackage : scenarioPackages) {
                packages.put(scenarioPackage.packageId(), scenarioPackage);
            }
        }

        @Override
        public Optional<ScenarioPackage> findByInputFingerprint(String fingerprint) {
            return packages.values().stream().filter(candidate -> candidate.inputFingerprint().equals(fingerprint)).findFirst();
        }

        @Override
        public Optional<ScenarioPackage> findById(UUID packageId) {
            return Optional.ofNullable(packages.get(packageId));
        }

        @Override
        public void save(ScenarioPackage scenarioPackage) {
            packages.put(scenarioPackage.packageId(), scenarioPackage);
        }
    }

    private static final class InMemoryBindingRepository implements RuntimeBindingRepository {
        private RuntimeBinding current;

        @Override
        public Optional<RuntimeBinding> findCurrentByAdventureId(AdventureId adventureId) {
            return current != null && current.adventureId().equals(adventureId) ? Optional.of(current) : Optional.empty();
        }

        @Override
        public List<RuntimeBinding> findAllByAdventureId(AdventureId adventureId) {
            return current != null && current.adventureId().equals(adventureId) ? List.of(current) : List.of();
        }

        @Override
        public void save(RuntimeBinding binding) {
            current = binding;
        }
    }
}
