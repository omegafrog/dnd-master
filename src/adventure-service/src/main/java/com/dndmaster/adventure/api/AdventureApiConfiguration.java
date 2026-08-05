package com.dndmaster.adventure.api;

import com.dndmaster.adventure.application.combat.*;
import com.dndmaster.adventure.application.knowledge.*;
import com.dndmaster.adventure.application.guidance.*;
import com.dndmaster.adventure.application.progress.*;
import com.dndmaster.adventure.application.ruleset.*;
import com.dndmaster.adventure.application.runtime.*;
import com.dndmaster.adventure.application.scenario.preparation.RuntimeOptionCatalogPort;
import com.dndmaster.adventure.application.scenario.preparation.ScenarioPreparationApplicationService;
import com.dndmaster.adventure.application.scenario.preparation.StaticRuntimeOptionCatalog;
import com.dndmaster.adventure.application.saved.*;
import com.dndmaster.adventure.application.session.*;
import com.dndmaster.adventure.application.storyplan.AdventureStoryPlanApplicationService;
import com.dndmaster.adventure.application.storyplan.AdventureStoryPlanRepository;
import com.dndmaster.adventure.application.scenario.*;
import com.dndmaster.adventure.domain.adventure.Adventure;
import com.dndmaster.adventure.domain.adventure.ActiveSourceContext;
import com.dndmaster.adventure.domain.inquiry.RulebookId;
import com.dndmaster.adventure.domain.scenario.ScenarioSource;
import com.dndmaster.adventure.infrastructure.persistence.PostgresAdventureRepository;
import com.dndmaster.adventure.infrastructure.persistence.PostgresScenarioBundleRepository;
import com.dndmaster.adventure.infrastructure.persistence.PostgresScenarioPackageRepository;
import com.dndmaster.adventure.infrastructure.persistence.PostgresResolutionOverrideRepository;
import com.dndmaster.adventure.infrastructure.persistence.PostgresScenarioCompilationRepository;
import com.dndmaster.adventure.infrastructure.persistence.PostgresRuntimeBindingRepository;
import com.dndmaster.adventure.infrastructure.persistence.PostgresRuntimeTurnRepository;
import com.dndmaster.adventure.infrastructure.persistence.PostgresRuntimeCommandJournal;
import com.dndmaster.adventure.infrastructure.persistence.PostgresGmTurnRepository;
import com.dndmaster.adventure.infrastructure.persistence.PostgresSessionEventRepository;
import com.dndmaster.adventure.infrastructure.persistence.PostgresWorkQueueAdapter;
import com.dndmaster.adventure.infrastructure.persistence.PostgresSessionKnowledgeSetRepository;
import com.dndmaster.adventure.infrastructure.persistence.PostgresAdventureSessionRepository;
import com.dndmaster.adventure.infrastructure.persistence.PostgresAdventureSessionStartOutboxRepository;
import com.dndmaster.adventure.infrastructure.persistence.PostgresAdventureStoryPlanRepository;
import com.dndmaster.adventure.infrastructure.integration.CrossContextHttpKnowledgeDocumentLookupGateway;
import com.dndmaster.adventure.infrastructure.integration.CrossContextHttpPlayerSessionLookupGateway;
import com.dndmaster.adventure.infrastructure.integration.CrossContextHttpLegacyScenarioIngestionGateway;
import com.dndmaster.adventure.infrastructure.integration.CrossContextHttpInitialSourceContextProposalGateway;
import com.dndmaster.adventure.infrastructure.integration.CrossContextHttpResolutionExtractionGateway;
import com.dndmaster.adventure.infrastructure.integration.CrossContextHttpCharacterInputTagExtractionGateway;
import com.dndmaster.adventure.infrastructure.integration.CrossContextHttpScenarioSourceExcerptGateway;
import com.dndmaster.adventure.infrastructure.integration.CrossContextHttpCharacterContextSearchGateway;
import com.dndmaster.adventure.infrastructure.integration.CrossContextHttpRuleIntentClassificationGateway;
import com.dndmaster.adventure.infrastructure.integration.CrossContextHttpRuntimeEvidenceSearchGateway;
import com.dndmaster.adventure.infrastructure.integration.CrossContextHttpCharacterSheetReadGateway;
import com.dndmaster.adventure.infrastructure.integration.CrossContextHttpCharacterSheetOwnershipGateway;
import com.dndmaster.adventure.infrastructure.integration.CrossContextHttpCharacterSheetDeletionGateway;
import com.dndmaster.adventure.infrastructure.integration.CrossContextHttpAgentActionCandidateGateway;
import com.dndmaster.adventure.infrastructure.integration.HttpGmAgentPort;
import com.dndmaster.adventure.infrastructure.integration.HttpDiceToolPort;
import com.dndmaster.adventure.infrastructure.integration.HttpCharacterToolPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import java.util.List;
import com.dndmaster.adventure.application.storyplan.AdventureStoryPlanGenerationPort;
import com.dndmaster.adventure.infrastructure.integration.CrossContextHttpAdventureStoryPlanGenerationGateway;
import com.dndmaster.adventure.application.prologue.AdventurePrologueApplicationService;
import com.dndmaster.adventure.application.prologue.AdventurePrologueGenerationPort;

@Configuration(proxyBeanMethods = false)
public class AdventureApiConfiguration {

    @Bean
    PlatformTransactionManager transactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean
    AdventureRepository adventureRepository(DataSource dataSource) {
        return new PostgresAdventureRepository(dataSource);
    }

    @Bean
    GmTurnRepository gmTurnRepository(DataSource dataSource, ObjectMapper objectMapper) {
        return new PostgresGmTurnRepository(dataSource, objectMapper);
    }

    @Bean
    SessionEventRepository sessionEventRepository(DataSource dataSource) {
        return new PostgresSessionEventRepository(dataSource);
    }

    @Bean
    GmTurnFailureRecorder gmTurnFailureRecorder(GmTurnRepository turns, SessionEventRepository events) {
        return new GmTurnFailureRecorder(turns, events);
    }

    @Bean
    AdventureSessionRepository adventureSessionRepository(DataSource dataSource) {
        return new PostgresAdventureSessionRepository(dataSource);
    }

    @Bean
    AdventureSessionApplicationService adventureSessionApplicationService(
            AdventureSessionRepository repository,
            com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageRepository packageRepository,
            AdventureRepository adventureRepository,
            RuntimeBindingApplicationService runtimeBindingApplicationService,
            AdventureSessionStartOutboxRepository startOutboxRepository,
            CharacterSheetOwnershipPort ownershipPort,
            AdventureStoryPlanRepository storyPlanRepository,
            SessionKnowledgeSetRepository sessionKnowledgeSetRepository,
            AdventurePrologueApplicationService prologueService) {
        return new AdventureSessionApplicationService(repository, packageRepository, adventureRepository, runtimeBindingApplicationService, new AdventureSessionStartCoordinator(startOutboxRepository), ownershipPort, storyPlanRepository, sessionKnowledgeSetRepository, prologueService);
    }

    @Bean
    AdventurePrologueApplicationService adventurePrologueApplicationService(AdventureRepository adventures,
            AdventureStoryPlanRepository plans, CharacterSheetReadPort sheets, AdventurePrologueGenerationPort generator) {
        return new AdventurePrologueApplicationService(adventures, plans, sheets, generator);
    }

    @Bean
    AdventurePrologueGenerationPort adventurePrologueGenerationPort() {
        return request -> {
            var names = request.party().stream().map(snapshot -> snapshot.name() + " (레벨 " + snapshot.level() + ")").toList();
            var stage = request.stage();
            return String.format("%s. %s. %s. 함께한 모험가: %s. 근거: %s.", stage.title(), stage.goal(), stage.conflict(), String.join(", ", names), String.join(", ", request.evidence()));
        };
    }

    @Bean
    AdventureStoryPlanRepository adventureStoryPlanRepository(DataSource dataSource) {
        return new PostgresAdventureStoryPlanRepository(dataSource);
    }

    @Bean
    AdventureClockRepository adventureClockRepository(DataSource dataSource) {
        return new com.dndmaster.adventure.infrastructure.persistence.PostgresAdventureClockRepository(dataSource);
    }

    @Bean
    CommittedWorldFactRepository committedWorldFactRepository(DataSource dataSource) {
        return new com.dndmaster.adventure.infrastructure.persistence.PostgresCommittedWorldFactRepository(dataSource);
    }

    @Bean
    StoryContinuityContextProvider storyContinuityContextProvider(StoryPlanRevisionRepository revisions,
            AdventureStoryPlanRepository legacyPlans, AdventureClockRepository clocks, CommittedWorldFactRepository facts) {
        return sessionId -> revisions.current(sessionId).or(() -> legacyPlans.findBySessionId(new com.dndmaster.adventure.domain.adventure.SessionId(sessionId)).map(plan -> {
            var stages = plan.stages().stream().map(stage -> stage.title() + ":" + stage.goal() + ":" + stage.conflict()).toList();
            return new com.dndmaster.adventure.domain.runtime.plan.AdventureStoryPlanRevision(
                    plan.planId(), sessionId, plan.version(), null, plan.planId(), stages);
        })).map(revision -> {
            var clock = clocks.findBySessionId(sessionId).orElseGet(() -> com.dndmaster.adventure.domain.runtime.clock.AdventureClock.initial(sessionId));
            return new StoryContinuityContext(revision, facts.findBySessionId(sessionId).facts(), clock);
        });
    }

    @Bean
    StoryPlanRevisionRepository storyPlanRevisionRepository(DataSource dataSource, ObjectMapper mapper) {
        return new com.dndmaster.adventure.infrastructure.persistence.PostgresStoryPlanRevisionRepository(dataSource, mapper);
    }

    @Bean
    GmContextCheckpointRepository gmContextCheckpointRepository(DataSource dataSource, ObjectMapper mapper) {
        return new com.dndmaster.adventure.infrastructure.persistence.PostgresGmContextCheckpointRepository(dataSource, mapper);
    }

    @Bean
    ProviderTokenEstimator providerTokenEstimator() {
        return new ProviderTokenEstimator(Map.of("legacy", 8192, "local", 8192, "remote", 128000,
                "ollama", 8192, "openai", 128000));
    }

    @Bean
    GmContextCompactionScheduler gmContextCompactionScheduler() {
        return new GmContextCompactionScheduler(new CompactionPolicy(0.70));
    }

    @Bean
    ContextCompactionPort contextCompactionPort(
            @Value("${adventure.integration.ai-game-master.base-url:http://127.0.0.1:8080/}") String baseUrl,
            ObjectMapper objectMapper,
            @Value("${adventure.integration.internal-token:local-dev-internal-token}") String internalToken) {
        return new ValidatingContextCompactionPort(new com.dndmaster.adventure.infrastructure.integration.HttpGmContextCompactionPort(
                HttpClient.newHttpClient(), URI.create(baseUrl), Duration.ofSeconds(30), objectMapper, internalToken));
    }

    @Bean
    GmContextCheckpointApplicationService gmContextCheckpointApplicationService(
            ContextCompactionPort port, GmContextCheckpointRepository repository) {
        return new GmContextCheckpointApplicationService(new CompactionPolicy(0.70), port, repository);
    }

    @Bean
    AuthoritativeSnapshotResolver authoritativeSnapshotResolver(RuntimeTurnRepository turns,
            StoryContinuityContextProvider continuity, AdventureRepository adventures,
            CharacterSheetReadPort characterSheets, CombatMapViewPort maps) {
        return sessionId -> turns.findAllBySessionId(sessionId).stream()
                .reduce((first, second) -> second)
                .map(turn -> {
                    var adventure = adventures.findById(turn.adventureId()).orElseThrow(() -> new IllegalStateException("adventure not found"));
                    var characters = adventure.party().stream().map(member -> characterSheets.read(member.characterSheetId())).toList();
                    long characterVersion = characters.stream().mapToLong(CharacterSheetReadPort.CharacterSheet::version).max().orElse(0);
                    String characterSnapshot = characters.stream().map(sheet -> sheet.id().value() + ":" + sheet.name() + ":level=" + sheet.level() + ":version=" + sheet.version()).reduce((a, b) -> a + "|" + b).orElse("characters=none");
                    var map = maps.playerView(turn.adventureId().value(), adventure.ownerPlayerId().value());
                    String facts = continuity.load(sessionId).map(context -> context.promptText()).orElse("facts=none");
                    String clock = continuity.load(sessionId).map(context -> "clockVersion=" + context.clock().version()
                            + "; elapsedTurns=" + context.clock().turnsElapsed() + "; elapsedSeconds=" + context.clock().secondsElapsed()).orElse("clock=none");
                    return new VersionedRuntimeSnapshots(
                            characterSnapshot, characterVersion,
                            map.map(Object::toString).orElse("map=none"), map.map(CombatMapViewPort.View::version).orElse(0L),
                            facts, continuity.load(sessionId).map(context -> context.facts().stream().mapToLong(fact -> fact.version()).max().orElse(0)).orElse(0L),
                            clock, continuity.load(sessionId).map(context -> context.clock().version()).orElse(0L));
                })
                .orElse(new VersionedRuntimeSnapshots("", 0, "", 0, "", 0, "", 0));
    }

    @Bean
    RuntimeTurnCompactionCoordinator runtimeTurnCompactionCoordinator(
            ProviderTokenEstimator estimator, GmContextCompactionScheduler scheduler,
            GmContextCheckpointApplicationService checkpoints, AuthoritativeSnapshotResolver snapshots,
            StoryPlanRevisionRepository plans) {
        return new RuntimeTurnCompactionCoordinator(estimator, scheduler, checkpoints, snapshots, plans);
    }

    @Bean
    GmContextResumePromptProvider gmContextResumePromptProvider(GmContextCheckpointRepository repository,
            AuthoritativeSnapshotResolver snapshots, ResumedGmContextAssembler assembler) {
        return sessionId -> repository.current(sessionId).map(checkpoint -> {
            var current = snapshots.resolve(sessionId);
            var resumed = assembler.assemble(checkpoint, new AuthoritativeRuntimeSnapshots(
                    current.characterSnapshot(), current.mapSnapshot(), current.factSnapshot(), current.clockSnapshot(),
                    current.characterVersion(), current.mapVersion(), current.factVersion(), current.clockVersion()));
            return "checkpointSummary=" + resumed.summary() + "; exactTail=" + resumed.exactTail()
                    + "; characterSnapshot=" + resumed.characterSnapshot() + "; mapSnapshot=" + resumed.mapSnapshot()
                    + "; factSnapshot=" + resumed.factSnapshot() + "; clockSnapshot=" + resumed.clockSnapshot()
                    + "; planVersion=" + checkpoint.planVersion();
        }).orElse("");
    }

    @Bean
    ResumedGmContextAssembler resumedGmContextAssembler() {
        return new ResumedGmContextAssembler();
    }

    @Bean
    StoryContinuityCommandService storyContinuityCommandService(StoryPlanRevisionRepository plans,
            AdventureClockRepository clocks, CommittedWorldFactRepository facts,
            PlatformTransactionManager transactionManager,
            GameSystemDefinitionPort gameSystemDefinitionPort) {
        return new StoryContinuityCommandService(plans, clocks, facts,
                new com.dndmaster.adventure.domain.runtime.plan.StoryPlanRevisionValidator(),
                new org.springframework.transaction.support.TransactionTemplate(transactionManager),
                sessionId -> gameSystemDefinitionPort.find(sessionId)
                        .map(definition -> GameSystemTimeDefinitionAdapter.secondsPerTurn(definition.definitionJson()))
                        .filter(java.util.OptionalInt::isPresent).orElse(java.util.OptionalInt.empty()));
    }

    @Bean
    OfficialToolPort reviseStoryPlanToolPort(ObjectMapper mapper, StoryContinuityCommandService service) {
        return ContinuityToolHandlers.revise(mapper, service);
    }

    @Bean
    OfficialToolPort advanceGameTimeToolPort(ObjectMapper mapper, StoryContinuityCommandService service) {
        return ContinuityToolHandlers.advance(mapper, service);
    }

    @Bean
    AdventureStoryPlanApplicationService adventureStoryPlanApplicationService(AdventureStoryPlanRepository plans, AdventureSessionRepository sessions,
            com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageRepository packages, AdventureStoryPlanGenerationPort generator) {
        return new AdventureStoryPlanApplicationService(plans, sessions, packages, generator);
    }

    @Bean
    AdventureStoryPlanGenerationPort adventureStoryPlanGenerationPort(ObjectMapper mapper,
            @Value("${adventure.integration.ai-game-master.base-url:http://127.0.0.1:8080/}") String baseUrl,
            @Value("${adventure.integration.ai-game-master.story-plan-timeout:1800s}") Duration timeout) {
        return new CrossContextHttpAdventureStoryPlanGenerationGateway(HttpClient.newHttpClient(), URI.create(baseUrl), timeout, mapper);
    }

    @Bean
    CharacterSheetOwnershipPort characterSheetOwnershipPort(ObjectMapper objectMapper, @Value("${adventure.integration.character-management.base-url:http://127.0.0.1:8080/}") String baseUrl, @Value("${adventure.integration.internal-token:local-dev-internal-token}") String token) {
        return new CrossContextHttpCharacterSheetOwnershipGateway(HttpClient.newHttpClient(), URI.create(baseUrl), Duration.ofSeconds(10), objectMapper, token);
    }

    @Bean
    AdventureSessionStartOutboxRepository adventureSessionStartOutboxRepository(DataSource dataSource) {
        return new PostgresAdventureSessionStartOutboxRepository(dataSource);
    }

    @Bean
    CharacterSheetDeletionPort characterSheetDeletionPort(ObjectMapper objectMapper,
            @Value("${adventure.integration.character-management.base-url:http://127.0.0.1:8080/}") String baseUrl,
            @Value("${adventure.integration.internal-token:}") String token) {
        return new CrossContextHttpCharacterSheetDeletionGateway(HttpClient.newHttpClient(), URI.create(baseUrl), Duration.ofSeconds(10), objectMapper, token);
    }

    @Bean
    CharacterSheetDeletionWorker characterSheetDeletionWorker(AdventureSessionStartOutboxRepository outbox, CharacterSheetDeletionPort port) {
        return new CharacterSheetDeletionWorker(outbox, port);
    }

    @Bean
    SessionKnowledgeSetRepository sessionKnowledgeSetRepository(DataSource dataSource) {
        return new PostgresSessionKnowledgeSetRepository(dataSource);
    }

    @Bean
    ScenarioStoragePort scenarioStoragePort() {
        Map<String, byte[]> archive = new java.util.concurrent.ConcurrentHashMap<>();
        return new ScenarioStoragePort() {
            @Override
            public ScenarioSource store(ScenarioUpload upload) {
                String storageKey = "scenario-" + contentHash(upload.content());
                archive.put(storageKey, upload.content());
                return new ScenarioSource(storageKey, upload.originalFilename(), contentHash(upload.content()));
            }

            @Override
            public byte[] read(ScenarioSource source) {
                byte[] content = archive.get(source.storageKey());
                if (content == null) {
                    throw new IllegalStateException("scenario source is missing");
                }
                return content.clone();
            }
        };
    }

    @Bean
    ScenarioPreparationPort scenarioPreparationPort() {
        return source -> { /* TODO: implement scenario preparation */ };
    }

    @Bean
    AdventureScenarioRepository adventureScenarioRepository() {
        return new AdventureScenarioRepository() {
            private final java.util.Map<com.dndmaster.adventure.domain.scenario.ScenarioId,
                    com.dndmaster.adventure.domain.scenario.AdventureScenario> store = new java.util.concurrent.ConcurrentHashMap<>();

            @Override
            public java.util.Optional<com.dndmaster.adventure.domain.scenario.AdventureScenario> findById(
                    com.dndmaster.adventure.domain.scenario.ScenarioId id) {
                return java.util.Optional.ofNullable(store.get(id));
            }

            @Override
            public void save(com.dndmaster.adventure.domain.scenario.AdventureScenario scenario) {
                store.put(scenario.id(), scenario);
            }
        };
    }

    @Bean
    ScenarioBundleRepository scenarioBundleRepository(DataSource dataSource) {
        return new PostgresScenarioBundleRepository(dataSource);
    }

    @Bean
    com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageRepository scenarioPackageRepository(
            DataSource dataSource) {
        return new PostgresScenarioPackageRepository(dataSource);
    }

    @Bean
    com.dndmaster.adventure.application.scenario.compilation.ResolutionOverrideRepository resolutionOverrideRepository(
            DataSource dataSource) {
        return new PostgresResolutionOverrideRepository(dataSource);
    }

    @Bean
    com.dndmaster.adventure.application.scenario.compilation.ScenarioCompilationRepository scenarioCompilationRepository(
            DataSource dataSource) {
        return new PostgresScenarioCompilationRepository(dataSource);
    }

    @Bean
    RuntimeBindingRepository runtimeBindingRepository(DataSource dataSource, ObjectMapper objectMapper) {
        return new PostgresRuntimeBindingRepository(dataSource, objectMapper);
    }

    @Bean
    RuntimeTurnRepository runtimeTurnRepository(DataSource dataSource, ObjectMapper objectMapper) {
        return new PostgresRuntimeTurnRepository(dataSource, objectMapper);
    }

    @Bean
    RuntimeCommandJournal runtimeCommandJournal(DataSource dataSource, ObjectMapper objectMapper) {
        return new PostgresRuntimeCommandJournal(dataSource, objectMapper);
    }

    @Bean
    RuntimeCommandSagaApplicationService runtimeCommandSagaApplicationService(RuntimeCommandJournal journal) {
        return new RuntimeCommandSagaApplicationService(journal);
    }

    @Bean
    com.dndmaster.adventure.application.scenario.compilation.WorkQueuePort scenarioWorkQueue(DataSource dataSource) {
        return new PostgresWorkQueueAdapter(dataSource);
    }

    @Bean
    com.dndmaster.adventure.application.scenario.compilation.ScenarioCompilationProcessManager scenarioCompilationProcessManager(
            com.dndmaster.adventure.application.scenario.compilation.ScenarioCompilationRepository repository,
            com.dndmaster.adventure.application.scenario.compilation.WorkQueuePort queue) {
        return new com.dndmaster.adventure.application.scenario.compilation.ScenarioCompilationProcessManager(repository, queue);
    }

    @Bean
    com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageCompilationService scenarioPackageCompilationService(
            com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageRepository repository,
            com.dndmaster.adventure.application.scenario.compilation.ResolutionOverrideRepository overrideRepository) {
        return new com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageCompilationService(
                repository, overrideRepository);
    }

    @Bean
    com.dndmaster.adventure.application.scenario.compilation.ScenarioCompilationApplicationService scenarioCompilationApplicationService(
            ScenarioBundleRepository bundleRepository,
            com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageCompilationService compiler,
            com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageRepository packageRepository,
            com.dndmaster.adventure.application.scenario.compilation.ScenarioCompilationProcessManager processManager,
            com.dndmaster.adventure.application.scenario.compilation.ScenarioCompilationRepository compilationRepository,
            com.dndmaster.adventure.application.scenario.compilation.ScenarioSourceExcerptPort excerptPort,
            com.dndmaster.adventure.application.scenario.compilation.ResolutionOverrideRepository overrideRepository) {
        return new com.dndmaster.adventure.application.scenario.compilation.ScenarioCompilationApplicationService(
                bundleRepository, compiler, packageRepository, processManager, compilationRepository, excerptPort,
                overrideRepository);
    }

    @Bean
    RuntimeOptionCatalogPort runtimeOptionCatalogPort() {
        return new StaticRuntimeOptionCatalog();
    }

    @Bean
    ScenarioPreparationApplicationService scenarioPreparationApplicationService(
            com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageRepository packageRepository,
            ScenarioBundleRepository bundleRepository,
            RuntimeOptionCatalogPort runtimeOptionCatalogPort,
            com.dndmaster.adventure.application.scenario.compilation.CharacterContextSearchPort characterContextSearch,
            com.dndmaster.adventure.application.scenario.blueprint.CharacterInputTagExtractionPort characterTagExtraction,
            GameSystemDefinitionPort gameSystemDefinitionPort) {
        return new ScenarioPreparationApplicationService(packageRepository, bundleRepository, runtimeOptionCatalogPort,
                characterContextSearch, characterTagExtraction,
                new com.dndmaster.adventure.application.scenario.blueprint.CharacterCreationBlueprintCompiler(), gameSystemDefinitionPort);
    }

    @Bean
    AdventureScenarioApplicationService scenarioApplicationService(
            AdventureScenarioRepository repository,
            ScenarioStoragePort storagePort,
            ScenarioPreparationPort preparationPort) {
        return new AdventureScenarioApplicationService(repository, storagePort, preparationPort);
    }

    @Bean
    ScenarioBundleApplicationService scenarioBundleApplicationService(
            ScenarioBundleRepository repository, KnowledgeDocumentLookupPort lookupPort) {
        return new ScenarioBundleApplicationService(repository, lookupPort);
    }

    @Bean
    com.dndmaster.adventure.application.scenario.LegacyScenarioIngestionPort legacyScenarioIngestionPort(
            ObjectMapper objectMapper,
            @Value("${adventure.integration.rule-knowledge.base-url:http://127.0.0.1:8080/}") String baseUrl) {
        return new CrossContextHttpLegacyScenarioIngestionGateway(
                HttpClient.newHttpClient(),
                URI.create(baseUrl),
                Duration.ofSeconds(15),
                objectMapper);
    }

    @Bean
    com.dndmaster.adventure.application.scenario.LegacyScenarioMigrationApplicationService legacyScenarioMigrationApplicationService(
            AdventureScenarioRepository scenarioRepository,
            ScenarioStoragePort storagePort,
            com.dndmaster.adventure.application.scenario.LegacyScenarioIngestionPort ingestionPort,
            com.dndmaster.adventure.application.scenario.LegacyScenarioMigrationStateRepository stateRepository,
            ScenarioBundleApplicationService bundleService,
            com.dndmaster.adventure.application.scenario.compilation.ScenarioCompilationApplicationService compilationService,
            KnowledgeDocumentLookupPort lookupPort) {
        return new com.dndmaster.adventure.application.scenario.LegacyScenarioMigrationApplicationService(
                scenarioRepository, storagePort, ingestionPort, stateRepository, bundleService, compilationService, lookupPort);
    }

    @Bean
    com.dndmaster.adventure.application.scenario.LegacyScenarioMigrationStateRepository legacyScenarioMigrationStateRepository() {
        return new com.dndmaster.adventure.application.scenario.LegacyScenarioMigrationStateRepository() {
            private final Map<String, com.dndmaster.adventure.application.scenario.LegacyScenarioMigrationApplicationService.LegacyScenarioMigrationResult> store =
                    new java.util.concurrent.ConcurrentHashMap<>();

            @Override
            public java.util.Optional<com.dndmaster.adventure.application.scenario.LegacyScenarioMigrationApplicationService.LegacyScenarioMigrationResult> findByScenarioIdAndSourceHash(
                    com.dndmaster.adventure.domain.scenario.ScenarioId scenarioId, String sourceHash) {
                return java.util.Optional.ofNullable(store.get(key(scenarioId, sourceHash)));
            }

            @Override
            public void save(
                    com.dndmaster.adventure.domain.scenario.ScenarioId scenarioId,
                    String sourceHash,
                    com.dndmaster.adventure.application.scenario.LegacyScenarioMigrationApplicationService.LegacyScenarioMigrationResult result) {
                store.put(key(scenarioId, sourceHash), result);
            }

            private String key(com.dndmaster.adventure.domain.scenario.ScenarioId scenarioId, String sourceHash) {
                return scenarioId.value() + ":" + sourceHash;
            }
        };
    }

    @Bean
    com.dndmaster.adventure.application.scenario.compilation.ResolutionExtractionPort resolutionExtractionPort(
            ObjectMapper objectMapper,
            @Value("${adventure.integration.ai-game-master.base-url:http://127.0.0.1:8080/}") String baseUrl,
            @Value("${adventure.integration.scenario-compilation.timeout:120s}") Duration timeout) {
        return new CrossContextHttpResolutionExtractionGateway(
                HttpClient.newHttpClient(),
                URI.create(baseUrl),
                timeout,
                objectMapper);
    }

    @Bean
    com.dndmaster.adventure.application.scenario.blueprint.CharacterInputTagExtractionPort characterInputTagExtractionPort(
            ObjectMapper objectMapper,
            @Value("${adventure.integration.ai-game-master.base-url:http://127.0.0.1:8080/}") String baseUrl,
            @Value("${adventure.integration.scenario-compilation.timeout:120s}") Duration timeout) {
        return new CrossContextHttpCharacterInputTagExtractionGateway(HttpClient.newHttpClient(), URI.create(baseUrl), timeout, objectMapper);
    }

    com.dndmaster.adventure.application.scenario.compilation.ResolutionExtractionPort resolutionExtractionPort(
            ObjectMapper objectMapper, String baseUrl) {
        return resolutionExtractionPort(objectMapper, baseUrl, Duration.ofSeconds(120));
    }

    @Bean
    com.dndmaster.adventure.application.scenario.compilation.ScenarioSourceExcerptPort scenarioSourceExcerptPort(
            ObjectMapper objectMapper,
            @Value("${adventure.integration.rule-knowledge.base-url:http://127.0.0.1:8080/}") String baseUrl,
            @Value("${adventure.integration.scenario-compilation.timeout:120s}") Duration timeout) {
        return new CrossContextHttpScenarioSourceExcerptGateway(
                HttpClient.newHttpClient(), URI.create(baseUrl),
                timeout, objectMapper);
    }

    com.dndmaster.adventure.application.scenario.compilation.ScenarioSourceExcerptPort scenarioSourceExcerptPort(
            ObjectMapper objectMapper, String baseUrl) {
        return scenarioSourceExcerptPort(objectMapper, baseUrl, Duration.ofSeconds(120));
    }

    @Bean
    com.dndmaster.adventure.application.scenario.compilation.CharacterContextSearchPort characterContextSearchPort(
            ObjectMapper objectMapper,
            @Value("${adventure.integration.rule-knowledge.base-url:http://127.0.0.1:8080/}") String baseUrl,
            @Value("${adventure.integration.scenario-compilation.timeout:120s}") Duration timeout) {
        return new CrossContextHttpCharacterContextSearchGateway(
                HttpClient.newHttpClient(), URI.create(baseUrl), timeout, objectMapper);
    }

    @Bean
    com.dndmaster.adventure.application.scenario.compilation.ScenarioCompilationWorker scenarioCompilationWorker(
            com.dndmaster.adventure.application.scenario.compilation.ScenarioCompilationProcessManager processManager,
            com.dndmaster.adventure.application.scenario.compilation.ScenarioCompilationRepository compilationRepository,
            com.dndmaster.adventure.application.scenario.compilation.WorkQueuePort queue,
            ScenarioBundleRepository bundleRepository,
            com.dndmaster.adventure.application.scenario.compilation.ResolutionExtractionPort extractionPort,
            com.dndmaster.adventure.application.scenario.compilation.ScenarioSourceExcerptPort excerptPort,
            com.dndmaster.adventure.application.scenario.blueprint.CharacterInputTagExtractionPort characterInputTagExtractionPort,
            com.dndmaster.adventure.application.scenario.compilation.CharacterContextSearchPort characterContextSearchPort,
            com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageCompilationService compiler,
            com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageRepository packageRepository) {
        return new com.dndmaster.adventure.application.scenario.compilation.ScenarioCompilationWorker(
                processManager, compilationRepository, queue, bundleRepository, extractionPort, excerptPort,
                characterInputTagExtractionPort, characterContextSearchPort, compiler,
                packageRepository);
    }

    @Bean
    SavedAdventureApplicationService savedAdventureApplicationService(AdventureRepository repository) {
        return new SavedAdventureApplicationService(repository, false);
    }

    @Bean
    KnowledgeDocumentLookupPort knowledgeDocumentLookupPort(
            ObjectMapper objectMapper,
            @Value("${adventure.integration.rule-knowledge.base-url:http://127.0.0.1:8080/}") String baseUrl) {
        return new CrossContextHttpKnowledgeDocumentLookupGateway(
                HttpClient.newHttpClient(),
                URI.create(baseUrl),
                Duration.ofSeconds(2),
                objectMapper);
    }

    @Bean
    com.dndmaster.adventure.application.auth.PlayerSessionLookupPort playerSessionLookupPort(
            ObjectMapper objectMapper,
            @Value("${adventure.integration.identity-access.base-url:http://127.0.0.1:8080/}") String baseUrl) {
        return new CrossContextHttpPlayerSessionLookupGateway(
                HttpClient.newHttpClient(),
                URI.create(baseUrl),
                Duration.ofSeconds(2),
                objectMapper);
    }

    @Bean
    SessionKnowledgeSetApplicationService sessionKnowledgeSetApplicationService(
            AdventureRepository adventureRepository,
            SessionKnowledgeSetRepository sessionKnowledgeSetRepository,
            KnowledgeDocumentLookupPort lookupPort) {
        return new SessionKnowledgeSetApplicationService(adventureRepository, sessionKnowledgeSetRepository, lookupPort);
    }

    @Bean
    AdventureReadinessPort adventureReadinessPort(RuntimeBindingRepository runtimeBindingRepository) {
        return adventure -> runtimeBindingRepository.findCurrentByAdventureId(adventure.id())
                .map(binding -> new AdventureReadiness(!binding.playabilityReport().isBlocked(), true, true))
                .orElse(new AdventureReadiness(false, true, true));
    }

    @Bean
    RuleIntentClassificationPort ruleIntentClassificationPort(
            ObjectMapper objectMapper,
            @Value("${adventure.integration.ai-game-master.base-url:http://127.0.0.1:8080/}") String baseUrl) {
        return new CrossContextHttpRuleIntentClassificationGateway(
                HttpClient.newHttpClient(),
                URI.create(baseUrl),
                Duration.ofSeconds(2),
                objectMapper);
    }

    @Bean
    AiGameMasterPort aiGameMasterPort() {
        return new AiGameMasterPort() {
            @Override
            public SceneProgress advanceScene(SceneProgressRequest request) {
                return new SceneProgress(request.scenarioId(), "scene-content", "npc-state");
            }

            @Override
            public ActionJudgment adjudicate(ActionJudgmentRequest request) {
                return new ActionJudgment(request.ruleSetId(), "judgment-result");
            }
        };
    }

    private static String contentHash(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }

    @Bean
    InitialSourceContextProposalPort initialSourceContextProposalPort(
            ObjectMapper objectMapper,
            @Value("${adventure.integration.ai-game-master.base-url:http://127.0.0.1:8080/}") String baseUrl) {
        return new CrossContextHttpInitialSourceContextProposalGateway(
                HttpClient.newHttpClient(),
                URI.create(baseUrl),
                Duration.ofSeconds(2),
                objectMapper);
    }

    @Bean
    RuntimeBindingApplicationService runtimeBindingApplicationService(
            AdventureRepository adventureRepository,
            ScenarioBundleRepository bundleRepository,
            com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageRepository packageRepository,
            RuntimeBindingRepository runtimeBindingRepository,
            InitialSourceContextProposalPort proposalPort,
            KnowledgeDocumentLookupPort lookupPort,
            GameSystemDefinitionPort gameSystemDefinitionPort) {
        return new RuntimeBindingApplicationService(
                adventureRepository, bundleRepository, packageRepository, runtimeBindingRepository, proposalPort,
                lookupPort, gameSystemDefinitionPort);
    }

    @Bean
    GameSystemDefinitionPort gameSystemDefinitionPort(
            AdventureSessionRepository sessions, RuntimeBindingRepository bindings, ObjectMapper objectMapper,
            @Value("${adventure.integration.rule-knowledge.base-url:http://127.0.0.1:8080/}") String baseUrl,
            @Value("${adventure.integration.rule-knowledge.timeout-seconds:30}") long timeoutSeconds,
            @Value("${adventure.integration.internal-token:}") String internalToken) {
        return new com.dndmaster.adventure.infrastructure.integration.CrossContextHttpRulebookTimeDefinitionGateway(
                sessions, bindings, HttpClient.newHttpClient(), URI.create(baseUrl), Duration.ofSeconds(timeoutSeconds), objectMapper, internalToken);
    }

    @Bean
    RuntimeEvidenceSearchPort runtimeEvidenceSearchPort(
            ObjectMapper objectMapper,
            @Value("${adventure.integration.rule-knowledge.base-url:http://127.0.0.1:8080/}") String baseUrl,
            @Value("${adventure.integration.rule-knowledge.timeout-seconds:30}") long timeoutSeconds) {
        return new CrossContextHttpRuntimeEvidenceSearchGateway(
                HttpClient.newHttpClient(), URI.create(baseUrl), Duration.ofSeconds(timeoutSeconds), objectMapper);
    }

    @Bean
    GmAgentPort gmAgentPort(
            ObjectMapper objectMapper,
            @Value("${adventure.integration.ai-game-master.base-url:http://127.0.0.1:8080/}") String baseUrl,
            @Value("${adventure.integration.ai-game-master.timeout-seconds:30}") long timeoutSeconds,
            @Value("${adventure.integration.internal-token:local-dev-internal-token}") String internalToken) {
        return new HttpGmAgentPort(HttpClient.newHttpClient(), URI.create(baseUrl), Duration.ofSeconds(timeoutSeconds), objectMapper, internalToken);
    }

    @Bean
    OfficialToolPort diceToolPort(
            ObjectMapper objectMapper,
            @Value("${adventure.integration.dice-roll.base-url:http://127.0.0.1:8080/}") String baseUrl,
            @Value("${adventure.integration.internal-token:}") String token) {
        return new HttpDiceToolPort(HttpClient.newHttpClient(), URI.create(baseUrl), Duration.ofSeconds(15), objectMapper, token);
    }

    @Bean
    OfficialToolPort characterToolPort(
            ObjectMapper objectMapper,
            @Value("${adventure.integration.character-management.base-url:http://127.0.0.1:8080/}") String baseUrl,
            @Value("${adventure.integration.internal-token:local-dev-internal-token}") String token) {
        return new HttpCharacterToolPort(HttpClient.newHttpClient(), URI.create(baseUrl), Duration.ofSeconds(15), objectMapper, token);
    }

    @Bean
    GmToolGateway gmToolGateway(@Qualifier("diceToolPort") OfficialToolPort diceToolPort,
                                @Qualifier("characterToolPort") OfficialToolPort characterToolPort,
                                @Qualifier("reviseStoryPlanToolPort") OfficialToolPort reviseStoryPlanToolPort,
                                @Qualifier("advanceGameTimeToolPort") OfficialToolPort advanceGameTimeToolPort,
                                ObjectMapper objectMapper) {
        var definitions = new java.util.HashSet<>(OfficialGmToolRegistry.definitions(diceToolPort, characterToolPort));
        definitions.addAll(StoryContinuityToolRegistry.definitions(reviseStoryPlanToolPort, advanceGameTimeToolPort));
        return new GmToolGatewayService(definitions, java.time.Clock.systemUTC(), objectMapper);
    }

    @Bean
    RuntimePlanningPort runtimePlanningPort(GmAgentPort gmAgentPort, GmToolGateway gmToolGateway,
                                            RuntimeCommandSagaApplicationService saga) {
        return new GmAgentRuntimePlanningAdapter(gmAgentPort, new GmFinalValidator(), gmToolGateway, saga);
    }

    @Bean
    NarrationSafetyPort narrationSafetyPort() {
        return request -> {
            boolean approved = request.narration() != null
                    && !request.narration().isBlank()
                    && !request.narration().contains("\"")
                    && !request.narration().contains("“")
                    && !request.narration().contains("”");
            return new NarrationSafetyAssessment(approved, approved ? "approved" : "narration failed safety check");
        };
    }

    @Bean
    RuntimeTurnApplicationService runtimeTurnApplicationService(
            AdventureRepository adventureRepository,
            RuntimeBindingRepository runtimeBindingRepository,
            com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageRepository packageRepository,
            RuntimeTurnRepository runtimeTurnRepository,
            SessionEventRepository sessionEventRepository,
            RuntimeEvidenceSearchPort runtimeEvidenceSearchPort,
            RuntimePlanningPort runtimePlanningPort,
            NarrationSafetyPort narrationSafetyPort,
            SessionKnowledgeSetRepository sessionKnowledgeSetRepository,
            AdventureStoryPlanRepository storyPlanRepository,
            StoryContinuityContextProvider continuityContextProvider,
            RuntimeTurnCompactionCoordinator compactionCoordinator,
            GmContextResumePromptProvider resumePromptProvider) {
        return new RuntimeTurnApplicationService(
                adventureRepository, runtimeBindingRepository, packageRepository, runtimeTurnRepository, runtimeEvidenceSearchPort,
                runtimePlanningPort, narrationSafetyPort, sessionKnowledgeSetRepository, storyPlanRepository, continuityContextProvider,
                compactionCoordinator, resumePromptProvider);
    }

    @Bean
    CharacterSheetReadPort characterSheetReadPort(
            ObjectMapper objectMapper,
            @Value("${adventure.integration.character-management.base-url:http://127.0.0.1:8080/}") String baseUrl) {
        return new CrossContextHttpCharacterSheetReadGateway(
                HttpClient.newHttpClient(), URI.create(baseUrl), Duration.ofSeconds(10), objectMapper);
    }

    @Bean
    AgentActionCandidatePort agentActionCandidatePort(
            ObjectMapper objectMapper,
            @Value("${adventure.integration.ai-game-master.base-url:http://127.0.0.1:8080/}") String baseUrl) {
        return new CrossContextHttpAgentActionCandidateGateway(
                HttpClient.newHttpClient(), URI.create(baseUrl), Duration.ofSeconds(30), objectMapper);
    }

    @Bean
    AgentTurnApplicationService agentTurnApplicationService(
            CharacterSheetReadPort characterSheetReadPort,
            AgentActionCandidatePort agentActionCandidatePort,
            RuntimeTurnApplicationService runtimeTurnApplicationService,
            AdventureRepository adventureRepository) {
        return new AgentTurnApplicationService(characterSheetReadPort, agentActionCandidatePort, runtimeTurnApplicationService, adventureRepository);
    }

    @Bean
    RuleSetSearchScopePort ruleSetSearchScopePort() {
        return (adventureId, ruleSetId, owner) -> new RuleSearchScope(true, List.of());
    }

    @Bean
    RuleEvidenceSearchPort ruleEvidenceSearchPort() {
        return (owner, rulebooks, situation, queryIntent) -> List.of();
    }

    @Bean
    RuleAnswerCompositionPort ruleAnswerCompositionPort() {
        return (situation, evidence) -> new GuidanceComposition(
                com.dndmaster.adventure.domain.inquiry.EvidenceStatus.INSUFFICIENT, null, List.of());
    }

    @Bean
    RuleInquiryRepository ruleInquiryRepository() {
        return new RuleInquiryRepository() {
            private final java.util.Map<com.dndmaster.adventure.domain.inquiry.InquiryId,
                    com.dndmaster.adventure.domain.inquiry.RuleInquiry> store = new java.util.concurrent.ConcurrentHashMap<>();

            @Override
            public java.util.Optional<com.dndmaster.adventure.domain.inquiry.RuleInquiry> findById(
                    com.dndmaster.adventure.domain.inquiry.InquiryId id) {
                return java.util.Optional.ofNullable(store.get(id));
            }

            @Override
            public void save(com.dndmaster.adventure.domain.inquiry.RuleInquiry inquiry) {
                store.put(inquiry.id(), inquiry);
            }
        };
    }

    @Bean
    RuleGuidanceApplicationService guidanceApplicationService(
            RuleInquiryRepository repository,
            RuleSetSearchScopePort scopePort,
            RuleIntentClassificationPort intentPort,
            RuleEvidenceSearchPort searchPort,
            RuleAnswerCompositionPort compositionPort) {
        return new RuleGuidanceApplicationService(repository, scopePort, intentPort, searchPort, compositionPort);
    }

    @Bean
    CharacterCombatPort characterCombatPort() {
        return command -> { /* TODO: implement character verification */ };
    }

    @Bean
    DiceCombatPort diceCombatPort() {
        return command -> (int) (Math.random() * 20) + 1;
    }

    @Bean
    CombatMapPort combatMapPort(
            @Value("${adventure.integration.combat-map.base-url:http://127.0.0.1:8080/}") String baseUrl) {
        CrossContextHttpCombatGateway gateway = new CrossContextHttpCombatGateway(
                HttpClient.newHttpClient(), URI.create(baseUrl), Duration.ofSeconds(5));
        return gateway::validateAndMove;
    }

    @Bean
    CombatMapViewPort combatMapViewPort(
            @Value("${adventure.integration.combat-map.base-url:http://127.0.0.1:8080/}") String baseUrl,
            ObjectMapper objectMapper) {
        return new HttpCombatMapViewGateway(HttpClient.newHttpClient(), URI.create(baseUrl), Duration.ofSeconds(5), objectMapper);
    }

    @Bean
    AiCombatPort aiCombatPort() {
        return new AiCombatPort() {
            @Override
            public void controlState(CombatActionCommand command) { /* TODO */ }

            @Override
            public String adjudicate(CombatActionCommand command, int diceTotal) {
                return "judgment-result";
            }
        };
    }

    @Bean
    CombatOperationRepository combatOperationRepository() {
        return new CombatOperationRepository() {
            private final java.util.Map<java.util.UUID, CombatOperation> store = new java.util.concurrent.ConcurrentHashMap<>();

            @Override
            public java.util.Optional<CombatOperation> findById(java.util.UUID id) {
                return java.util.Optional.ofNullable(store.get(id));
            }

            @Override
            public void save(CombatOperation operation) {
                store.put(operation.id(), operation);
            }
        };
    }

    @Bean
    AdventureCombatApplicationService combatApplicationService(
            CombatOperationRepository repository,
            CharacterCombatPort characterPort,
            DiceCombatPort dicePort,
            CombatMapPort mapPort,
            AiCombatPort aiPort) {
        return new AdventureCombatApplicationService(repository, characterPort, dicePort, mapPort, aiPort);
    }

    @Bean
    RulebookOwnershipHttpPort rulebookOwnershipHttpPort() {
        return (rulebookId, owner) -> true;
    }

    @Bean
    AppliedRuleSetRepository appliedRuleSetRepository() {
        return new AppliedRuleSetRepository() {
            private final java.util.Map<com.dndmaster.adventure.domain.ruleset.RuleSetId,
                    com.dndmaster.adventure.domain.ruleset.AppliedRuleSet> store = new java.util.concurrent.ConcurrentHashMap<>();

            @Override
            public java.util.Optional<com.dndmaster.adventure.domain.ruleset.AppliedRuleSet> findById(
                    com.dndmaster.adventure.domain.ruleset.RuleSetId id) {
                return java.util.Optional.ofNullable(store.get(id));
            }

            @Override
            public void save(com.dndmaster.adventure.domain.ruleset.AppliedRuleSet ruleSet) {
                store.put(ruleSet.id(), ruleSet);
            }
        };
    }

    @Bean
    AppliedRuleSetApplicationService ruleSetApplicationService(
            AppliedRuleSetRepository repository,
            RulebookOwnershipHttpPort ownershipPort) {
        return new AppliedRuleSetApplicationService(repository, ownershipPort);
    }

    @Bean
    AdventureController adventureController(
            SavedAdventureApplicationService savedAdventureService,
            RuntimeTurnApplicationService runtimeTurnService,
            AdventureRepository adventureRepository,
            GmTurnFailureRecorder gmTurnFailureRecorder,
            GmTurnRepository gmTurnRepository,
            RuntimeTurnRepository runtimeTurnRepository,
            SessionEventRepository sessionEventRepository,
            RuleGuidanceApplicationService guidanceService,
            AdventureCombatApplicationService combatService,
            AdventureScenarioApplicationService scenarioService,
            AuthenticatedPlayerResolver playerResolver,
            org.springframework.beans.factory.ObjectProvider<CombatMapPort> combatMapPort,
            ObjectMapper objectMapper,
            org.springframework.beans.factory.ObjectProvider<CombatMapViewPort> combatMapViewPort) {
        return new AdventureController(
                savedAdventureService, runtimeTurnService, adventureRepository, gmTurnFailureRecorder, gmTurnRepository, runtimeTurnRepository, sessionEventRepository, guidanceService, combatService, scenarioService, playerResolver, combatMapPort, objectMapper, combatMapViewPort);
    }

    @Bean
    RuntimeBindingController runtimeBindingController(
            RuntimeBindingApplicationService service,
            AuthenticatedPlayerResolver playerResolver) {
        return new RuntimeBindingController(service, playerResolver);
    }
}
