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
import com.dndmaster.adventure.application.scenario.*;
import com.dndmaster.adventure.domain.adventure.Adventure;
import com.dndmaster.adventure.domain.adventure.ActiveSourceContext;
import com.dndmaster.adventure.domain.inquiry.RulebookId;
import com.dndmaster.adventure.domain.scenario.ScenarioSource;
import com.dndmaster.adventure.infrastructure.persistence.PostgresAdventureRepository;
import com.dndmaster.adventure.infrastructure.persistence.PostgresCombatEncounterRepository;
import com.dndmaster.adventure.infrastructure.persistence.PostgresCombatEventRepository;
import com.dndmaster.adventure.infrastructure.persistence.PostgresScenarioBundleRepository;
import com.dndmaster.adventure.infrastructure.persistence.PostgresScenarioPackageRepository;
import com.dndmaster.adventure.infrastructure.persistence.PostgresResolutionOverrideRepository;
import com.dndmaster.adventure.infrastructure.persistence.PostgresScenarioCompilationRepository;
import com.dndmaster.adventure.infrastructure.persistence.PostgresCompilationCandidateRepository;
import com.dndmaster.adventure.infrastructure.persistence.PostgresRuntimeBindingRepository;
import com.dndmaster.adventure.infrastructure.persistence.PostgresRuntimeTurnRepository;
import com.dndmaster.adventure.infrastructure.persistence.PostgresRuntimeTurnCommandRepository;
import com.dndmaster.adventure.infrastructure.persistence.PostgresRuntimeTurnFailureRepository;
import com.dndmaster.adventure.infrastructure.persistence.PostgresNarrativeStateRepository;
import com.dndmaster.adventure.infrastructure.persistence.PostgresRuntimeCommandJournal;
import com.dndmaster.adventure.infrastructure.persistence.PostgresGmTurnRepository;
import com.dndmaster.adventure.infrastructure.persistence.PostgresSessionEventRepository;
import com.dndmaster.adventure.infrastructure.persistence.PostgresWorkQueueAdapter;
import com.dndmaster.adventure.infrastructure.persistence.PostgresSessionKnowledgeSetRepository;
import com.dndmaster.adventure.infrastructure.persistence.PostgresAdventureSessionRepository;
import com.dndmaster.adventure.infrastructure.persistence.PostgresAdventureSessionStartOutboxRepository;
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
import com.dndmaster.adventure.infrastructure.integration.CrossContextHttpCharacterSheetOwnershipGateway;
import com.dndmaster.adventure.infrastructure.integration.CrossContextHttpCharacterSheetDeletionGateway;
import com.dndmaster.adventure.infrastructure.integration.HttpTypedRuntimeGmAgentPort;
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
    com.dndmaster.adventure.application.combat.CombatEncounterRepository combatEncounterRepository(DataSource dataSource) {
        return new PostgresCombatEncounterRepository(dataSource);
    }

    @Bean
    com.dndmaster.adventure.application.combat.CombatLifecycleApplicationService combatLifecycleApplicationService(
            com.dndmaster.adventure.application.combat.CombatEncounterRepository repository,
            com.dndmaster.adventure.application.combat.CombatEventRepository eventRepository) {
        return new com.dndmaster.adventure.application.combat.CombatLifecycleApplicationService(repository, eventRepository);
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
    com.dndmaster.adventure.application.combat.CombatEventRepository combatEventRepository(DataSource dataSource) {
        return new PostgresCombatEventRepository(dataSource);
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
    GmProviderBindingRepository gmProviderBindingRepository(DataSource dataSource) {
        return new com.dndmaster.adventure.infrastructure.persistence.PostgresGmProviderBindingRepository(dataSource);
    }

    @Bean
    GmProviderBindingService gmProviderBindingService(GmProviderBindingRepository repository) {
        return new GmProviderBindingService(repository);
    }

    @Bean
    GmProviderQualityGateService gmProviderQualityGateService(io.micrometer.core.instrument.MeterRegistry registry) {
        return new GmProviderQualityGateService(new com.dndmaster.adventure.infrastructure.metrics.MicrometerGmQualityMetrics(registry));
    }

    @Bean
    GmProviderQualityGateStartupValidator gmProviderQualityGateStartupValidator(
            GmProviderQualityGateService gate, ObjectMapper mapper,
            @Value("${adventure.gm.quality-gate.enforce:true}") boolean enforce) {
        return new GmProviderQualityGateStartupValidator(gate, mapper, enforce);
    }

    @Bean
    CharacterSheetOwnershipPort characterSheetOwnershipPort(ObjectMapper objectMapper, @Value("${adventure.integration.character-management.base-url:http://127.0.0.1:8080/}") String baseUrl, @Value("${adventure.integration.internal-token:${INTERNAL_SERVICE_TOKEN:}}") String token) {
        return new CrossContextHttpCharacterSheetOwnershipGateway(HttpClient.newHttpClient(), URI.create(baseUrl), Duration.ofSeconds(10), objectMapper, token);
    }

    @Bean
    com.dndmaster.adventure.application.session.AiCompanionGenerationPort aiCompanionGenerationPort(ObjectMapper objectMapper,
            @Value("${adventure.integration.ai-game-master.base-url:http://127.0.0.1:8080/}") String baseUrl,
            @Value("${adventure.integration.internal-token:${INTERNAL_SERVICE_TOKEN:}}") String token) {
        return new com.dndmaster.adventure.infrastructure.integration.CrossContextHttpAiCompanionGenerationGateway(
                HttpClient.newHttpClient(), URI.create(baseUrl), Duration.ofSeconds(30), objectMapper, token);
    }

    @Bean
    com.dndmaster.adventure.application.session.AiCompanionSheetCreationPort aiCompanionSheetCreationPort(
            ObjectMapper objectMapper, @Value("${adventure.integration.character-management.base-url:http://127.0.0.1:8080/}") String baseUrl,
            @Value("${adventure.integration.internal-token:${INTERNAL_SERVICE_TOKEN:}}") String token) {
        return new com.dndmaster.adventure.infrastructure.integration.CrossContextHttpAiCompanionCharacterSheetGateway(
                HttpClient.newHttpClient(), URI.create(baseUrl), Duration.ofSeconds(15), objectMapper, token);
    }

    @Bean
    AdventureSessionStartOutboxRepository adventureSessionStartOutboxRepository(DataSource dataSource) {
        return new PostgresAdventureSessionStartOutboxRepository(dataSource);
    }

    @Bean
    AdventureSessionApplicationService adventureSessionApplicationService(
            AdventureSessionRepository repository,
            com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageRepository packageRepository,
            AdventureRepository adventureRepository,
            RuntimeBindingApplicationService runtimeBindingService,
            AdventureSessionStartOutboxRepository startOutboxRepository,
            CharacterSheetOwnershipPort ownershipPort,
            SessionKnowledgeSetRepository sessionKnowledgeSetRepository,
            AiCompanionGenerationPort aiCompanionGenerationPort,
            AiCompanionSheetCreationPort aiCompanionSheetCreationPort,
            com.dndmaster.adventure.application.combat.CombatMapPreparationPort combatMapPreparationPort) {
        return new AdventureSessionApplicationService(repository, packageRepository, adventureRepository,
                runtimeBindingService, new AdventureSessionStartCoordinator(startOutboxRepository), ownershipPort,
                sessionKnowledgeSetRepository, aiCompanionGenerationPort, aiCompanionSheetCreationPort,
                combatMapPreparationPort);
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
    com.dndmaster.adventure.application.scenario.compilation.CompilationCandidateRepository compilationCandidateRepository(
            DataSource dataSource) {
        return new PostgresCompilationCandidateRepository(dataSource);
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
    RuntimeTurnCommandRepository runtimeTurnCommandRepository(DataSource dataSource) {
        return new PostgresRuntimeTurnCommandRepository(dataSource);
    }

    @Bean
    RuntimeTurnCommandAdapter runtimeTurnCommandAdapter(GmToolGateway gateway, ObjectMapper objectMapper,
            CombatMapPort combatMapPort) {
        return new RuntimeTurnCommandAdapterRegistry(
                Map.of("combat-map.move", new CombatMapRuntimeTurnCommandAdapter(combatMapPort, objectMapper)),
                new GmToolRuntimeTurnCommandAdapter(gateway, objectMapper));
    }

    @Bean
    RuntimeTurnCommitOrchestrator runtimeTurnCommitOrchestrator(RuntimeTurnRepository turnRepository,
            RuntimeTurnCommandRepository commandRepository, RuntimeTurnCommandAdapter adapter) {
        return new RuntimeTurnCommitOrchestrator(turnRepository, commandRepository, adapter);
    }

    @Bean
    NarrativeStateRepository narrativeStateRepository(DataSource dataSource, ObjectMapper objectMapper) {
        return new PostgresNarrativeStateRepository(dataSource, objectMapper);
    }

    @Bean
    RuntimeNarrativeStateApplicationService runtimeNarrativeStateApplicationService(
            NarrativeStateRepository repository) {
        return new RuntimeNarrativeStateApplicationService(repository);
    }

    @Bean
    RuntimeTurnFailureRepository runtimeTurnFailureRepository(DataSource dataSource) {
        return new PostgresRuntimeTurnFailureRepository(dataSource);
    }

    @Bean
    ApprovedPromptConfigurationReadPort approvedPromptConfigurationReadPort(
            org.springframework.core.env.Environment environment) {
        java.util.Map<String, com.dndmaster.adventure.application.runtime.ApprovedPromptConfiguration> configurations = new java.util.LinkedHashMap<>();
        for (String role : java.util.List.of("PLANNER", "JUDGE", "WRITER", "VERIFIER")) {
            String prefix = "adventure.runtime.prompt." + role.toLowerCase(java.util.Locale.ROOT) + ".";
            String promptVersion = environment.getProperty(prefix + "version");
            String modelVersion = environment.getProperty(prefix + "model");
            if (promptVersion == null || promptVersion.isBlank() || modelVersion == null || modelVersion.isBlank()) continue;
            long activationVersion = Long.parseLong(environment.getProperty(prefix + "activation-version", "1"));
            configurations.put(role, new com.dndmaster.adventure.application.runtime.ApprovedPromptConfiguration(
                    role, promptVersion, modelVersion, environment.getProperty(prefix + "optimization-run"),
                    environment.getProperty(prefix + "parent-version"), environment.getProperty(prefix + "dataset"),
                    environment.getProperty(prefix + "eval"), activationVersion));
        }
        return new com.dndmaster.adventure.application.runtime.EnvironmentApprovedPromptConfigurationReadPort(configurations);
    }

    @Bean
    RuntimeTurnFailurePersistence runtimeTurnFailurePersistence(RuntimeTurnRepository runtimeTurnRepository,
            RuntimeTurnFailureRepository failureRepository) {
        return new RuntimeTurnFailurePersistence(runtimeTurnRepository, failureRepository);
    }

    @Bean
    RuntimeTurnDiagnosticsApplicationService runtimeTurnDiagnosticsApplicationService(RuntimeTurnRepository turns,
            RuntimeTurnFailureRepository failures) {
        return new RuntimeTurnDiagnosticsApplicationService(turns, failures);
    }

    @Bean
    RuntimeCommandJournal runtimeCommandJournal(DataSource dataSource, ObjectMapper objectMapper) {
        return new PostgresRuntimeCommandJournal(dataSource, objectMapper);
    }

    @Bean
    RuntimeCommandSagaApplicationService runtimeCommandSagaApplicationService(RuntimeCommandJournal journal,
            io.micrometer.core.instrument.MeterRegistry registry) {
        return new RuntimeCommandSagaApplicationService(journal,
                new com.dndmaster.adventure.infrastructure.metrics.MicrometerGmQualityMetrics(registry));
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
            com.dndmaster.adventure.application.scenario.compilation.ResolutionOverrideRepository overrideRepository,
            GameSystemDefinitionPort gameSystemDefinitionPort) {
        return new com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageCompilationService(
                repository, overrideRepository, gameSystemDefinitionPort);
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
            com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageRepository packageRepository,
            com.dndmaster.adventure.application.scenario.compilation.CompilationCandidateRepository candidateRepository) {
        return new com.dndmaster.adventure.application.scenario.compilation.ScenarioCompilationWorker(
                processManager, compilationRepository, queue, bundleRepository, extractionPort, excerptPort,
                characterInputTagExtractionPort, characterContextSearchPort, compiler,
                packageRepository, candidateRepository);
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
            @Value("${adventure.integration.ai-game-master.timeout-seconds:180}") long timeoutSeconds,
            @Value("${adventure.integration.internal-token:}") String internalToken) {
        return new HttpTypedRuntimeGmAgentPort(
                HttpClient.newHttpClient(), URI.create(baseUrl), Duration.ofSeconds(timeoutSeconds), objectMapper, internalToken);
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
            @Value("${adventure.integration.internal-token:${INTERNAL_SERVICE_TOKEN:}}") String token) {
        return new HttpCharacterToolPort(HttpClient.newHttpClient(), URI.create(baseUrl), Duration.ofSeconds(15), objectMapper, token);
    }

    @Bean
    GmToolGateway gmToolGateway(@Qualifier("diceToolPort") OfficialToolPort diceToolPort,
                                @Qualifier("characterToolPort") OfficialToolPort characterToolPort,
                                ObjectMapper objectMapper) {
        var definitions = new java.util.HashSet<>(OfficialGmToolRegistry.definitions(diceToolPort, characterToolPort));
        return new GmToolGatewayService(definitions, java.time.Clock.systemUTC(), objectMapper);
    }

    @Bean
    NarrativeVerifierPort narrativeVerifier() {
        return new DefaultNarrativeVerifier(new DeterministicSemanticNarrativeVerifier());
    }

    @Bean
    RewritePort narrativeRewritePort() {
        return new BoundedNarrativeRewriteAdapter();
    }

    @Bean
    NarrativeVerificationAuditPort narrativeVerificationAuditPort() {
        return new LoggingNarrativeVerificationAuditPort();
    }

    @Bean
    ExemplarRetrievalAuditPort exemplarRetrievalAuditPort() {
        return new LoggingExemplarRetrievalAuditPort();
    }

    @Bean
    PlanAuditPort planAuditPort() {
        return new LoggingPlanAuditPort();
    }

    @Bean
    ExemplarRetrieverPort exemplarRetriever() {
        return new InMemoryExemplarCatalogIndexAdapter(java.util.List.of(
                new StyleExemplar("production-default", "The moment settles into a clear, playable beat.",
                        "scene", "action", "neutral", "steady", "short",
                        new Provenance("runtime-default", "style-exemplar", "1"), true)));
    }

    @Bean
    RuntimePlanningPort runtimePlanningPort(GmAgentPort gmAgentPort, GmToolGateway gmToolGateway,
                                            RuntimeCommandSagaApplicationService saga,
                                            @Value("${adventure.runtime.best-of-n.count:3}") int candidateCount,
                                            @Value("${adventure.runtime.best-of-n.simple:false}") boolean simpleTurn,
                                            PlanAuditPort planAuditPort) {
        RuntimePlanningPort planner = new GmAgentRuntimePlanningAdapter(gmAgentPort, new GmFinalValidator(), gmToolGateway, saga);
        return new BestOfNRuntimePlanningAdapter(planner, candidateCount, simpleTurn, planAuditPort);
    }

    @Bean
    NarrationSafetyPort narrationSafetyPort() {
        return request -> {
            String narration = request.narration();
            String reason = "approved";
            if (narration == null || narration.isBlank()) reason = "blank narration";
            else if (narration.contains("\"") || narration.contains("“") || narration.contains("”")) reason = "quotation mark detected";
            else if (com.dndmaster.adventure.application.runtime.NarrationLeakDetector
                    .isLikelySourceLeak(narration, request.evidencePack())) reason = "source leak or prohibited reference detected";
            boolean approved = "approved".equals(reason);
            return new NarrationSafetyAssessment(approved, reason);
        };
    }

    @Bean
    RuntimeTurnLockService runtimeTurnLockService(GmProviderBindingRepository repository) {
        return new RuntimeTurnLockService(repository);
    }

    @Bean
    RuntimeTurnApplicationService runtimeTurnApplicationService(
            AdventureRepository adventureRepository,
            RuntimeBindingRepository runtimeBindingRepository,
            com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageRepository packageRepository,
            RuntimeTurnRepository runtimeTurnRepository,
            RuntimeEvidenceSearchPort runtimeEvidenceSearchPort,
            RuntimePlanningPort runtimePlanningPort,
            NarrationSafetyPort narrationSafetyPort,
            SessionKnowledgeSetRepository sessionKnowledgeSetRepository,
            GmProviderBindingRepository providerBindingRepository,
            RuntimeTurnFailurePersistence failurePersistence,
            RuntimeNarrativeStateApplicationService narrativeStateService,
            ApprovedPromptConfigurationReadPort approvedPromptConfigurationReadPort,
            NarrativeVerifierPort narrativeVerifier,
            RewritePort narrativeRewritePort,
            NarrativeVerificationAuditPort narrativeVerificationAuditPort,
            ExemplarRetrieverPort exemplarRetriever,
            ExemplarRetrievalAuditPort exemplarRetrievalAuditPort,
            RuntimeTurnLockService runtimeTurnLockService,
            RuntimeTurnCommitOrchestrator commitOrchestrator) {
        RuntimeTurnApplicationService service = new RuntimeTurnApplicationService(
                adventureRepository, runtimeBindingRepository, packageRepository, runtimeTurnRepository, runtimeEvidenceSearchPort,
                runtimePlanningPort, narrationSafetyPort, sessionKnowledgeSetRepository, providerBindingRepository,
                new ScenarioRuntimeWriterAdapter(), narrativeVerifier, narrativeRewritePort, narrativeVerificationAuditPort,
                exemplarRetriever, exemplarRetrievalAuditPort, narrativeStateService);
        service.setFailurePersistence(failurePersistence);
        service.setApprovedPromptConfigurationReadPort(approvedPromptConfigurationReadPort);
        service.setTurnLockService(runtimeTurnLockService);
        service.setCommitOrchestrator(commitOrchestrator);
        return service;
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
    CharacterCombatPort characterCombatPort(
            @Value("${adventure.integration.character.base-url:http://127.0.0.1:8080/}") String baseUrl,
            @Value("${adventure.integration.internal-token:${INTERNAL_SERVICE_TOKEN:}}") String internalToken) {
        return new CrossContextHttpCombatGateway(
                HttpClient.newHttpClient(), URI.create(baseUrl), Duration.ofSeconds(5), internalToken);
    }

    @Bean
    DiceCombatPort diceCombatPort() {
        return command -> (int) (Math.random() * 20) + 1;
    }

    @Bean
    CombatMapPort combatMapPort(
            @Value("${adventure.integration.combat-map.base-url:http://127.0.0.1:8080/}") String baseUrl,
            @Value("${adventure.integration.internal-token:${INTERNAL_SERVICE_TOKEN:}}") String internalToken) {
        CrossContextHttpCombatGateway gateway = new CrossContextHttpCombatGateway(
                HttpClient.newHttpClient(), URI.create(baseUrl), Duration.ofSeconds(5), internalToken);
        return gateway::validateAndMove;
    }

    @Bean
    CombatMapViewPort combatMapViewPort(
            @Value("${adventure.integration.combat-map.base-url:http://127.0.0.1:8080/}") String baseUrl,
            @Value("${adventure.integration.internal-token:${INTERNAL_SERVICE_TOKEN:}}") String internalToken,
            ObjectMapper objectMapper) {
        return new HttpCombatMapViewGateway(HttpClient.newHttpClient(), URI.create(baseUrl), Duration.ofSeconds(5), objectMapper, internalToken);
    }

    @Bean
    com.dndmaster.adventure.application.combat.CombatMapPreparationPort combatMapPreparationPort(
            @Value("${adventure.integration.combat-map.base-url:http://127.0.0.1:8080/}") String baseUrl,
            @Value("${adventure.integration.internal-token:${INTERNAL_SERVICE_TOKEN:}}") String internalToken,
            ObjectMapper objectMapper) {
        return new com.dndmaster.adventure.application.combat.HttpCombatMapPreparationGateway(
                HttpClient.newHttpClient(), URI.create(baseUrl), Duration.ofSeconds(30), objectMapper, internalToken);
    }

    @Bean
    AiCombatPort aiCombatPort(
            @Value("${adventure.integration.combat-map.base-url:http://127.0.0.1:8080/}") String baseUrl,
            @Value("${adventure.integration.internal-token:${INTERNAL_SERVICE_TOKEN:}}") String internalToken) {
        return aiCombatPort(new CrossContextHttpCombatGateway(
                HttpClient.newHttpClient(), URI.create(baseUrl), Duration.ofSeconds(5), internalToken));
    }

    /** Unit-test factory retaining local adjudication without a remote state call. */
    AiCombatPort aiCombatPort() {
        return aiCombatPort(new AiCombatPort() {
            @Override public void controlState(CombatActionCommand command) { }
            @Override public String adjudicate(CombatActionCommand command, int diceTotal) { return ""; }
        });
    }

    private AiCombatPort aiCombatPort(AiCombatPort statePort) {
        return new AiCombatPort() {
            @Override
            public void controlState(CombatActionCommand command) {
                if (command.role() != CombatActorRole.PLAYER && command.movementPath() != null) {
                    statePort.controlState(command);
                }
            }

            @Override
            public String adjudicate(CombatActionCommand command, int diceTotal) {
                if (diceTotal == 20) return "critical hit (natural 20)";
                if (diceTotal == 1) return "critical miss (natural 1)";
                if (command.targetArmorClass() != null && command.attackModifier() != null) {
                    int total = diceTotal + command.attackModifier();
                    return (total >= command.targetArmorClass() ? "hit" : "miss")
                            + " (attack=" + total + ", AC=" + command.targetArmorClass() + ")";
                }
                return "판정 보류: 대상 AC와 공격 보정이 필요합니다 (d20=" + diceTotal + ").";
            }

            @Override
            public CombatOutcome adjudicateOutcome(CombatActionCommand command, int diceTotal) {
                String judgment = adjudicate(command, diceTotal);
                boolean criticalHit = diceTotal == 20;
                boolean hit = criticalHit || judgment.startsWith("hit (");
                int damageMultiplier = criticalHit ? 2 : 1;
                return new CombatOutcome(judgment, hit && command.damageAmount() != null
                        ? new CombatCharacterMutation(-damageMultiplier * command.damageAmount(), 0, java.util.List.of(), java.util.List.of())
                        : CombatCharacterMutation.none(), hit && command.damageAmount() != null && command.endCombat());
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
            @Qualifier("diceCombatPort") DiceCombatPort dicePort,
            @Qualifier("combatMapPort") CombatMapPort mapPort,
            @Qualifier("aiCombatPort") AiCombatPort aiPort) {
        return new AdventureCombatApplicationService(repository, characterPort, dicePort, mapPort, aiPort);
    }

    @Bean
    RulebookOwnershipHttpPort rulebookOwnershipHttpPort(
            ObjectMapper objectMapper,
            @Value("${adventure.integration.rule-knowledge.base-url:http://127.0.0.1:8080/}") String baseUrl,
            @Value("${adventure.integration.rule-knowledge.timeout-seconds:30}") long timeoutSeconds) {
        return new com.dndmaster.adventure.infrastructure.integration.CrossContextHttpRulebookOwnershipAdapter(
                HttpClient.newHttpClient(), URI.create(baseUrl), Duration.ofSeconds(timeoutSeconds), objectMapper);
    }

    @Bean
    AppliedRuleSetRepository appliedRuleSetRepository(DataSource dataSource) {
        return new com.dndmaster.adventure.infrastructure.persistence.PostgresAppliedRuleSetRepository(dataSource);
    }

    @Bean
    AppliedRuleSetApplicationService ruleSetApplicationService(
            AppliedRuleSetRepository repository) {
        return new AppliedRuleSetApplicationService(repository);
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
            org.springframework.beans.factory.ObjectProvider<CharacterCombatPort> characterCombatPort,
            ObjectMapper objectMapper,
            org.springframework.beans.factory.ObjectProvider<CombatMapViewPort> combatMapViewPort,
            org.springframework.beans.factory.ObjectProvider<org.springframework.transaction.PlatformTransactionManager> transactionManager,
            com.dndmaster.adventure.application.combat.CombatLifecycleApplicationService combatLifecycleService) {
        return new AdventureController(
                savedAdventureService, runtimeTurnService, adventureRepository, gmTurnFailureRecorder, gmTurnRepository, runtimeTurnRepository, sessionEventRepository, guidanceService, combatService, scenarioService, playerResolver, combatMapPort, characterCombatPort, objectMapper, combatMapViewPort, combatLifecycleService);
    }

    @Bean
    RuntimeBindingController runtimeBindingController(
            RuntimeBindingApplicationService service,
            AuthenticatedPlayerResolver playerResolver) {
        return new RuntimeBindingController(service, playerResolver);
    }
}
