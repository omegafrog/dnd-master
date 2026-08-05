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
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.List;
import com.dndmaster.adventure.application.storyplan.AdventureStoryPlanGenerationPort;
import com.dndmaster.adventure.infrastructure.integration.CrossContextHttpAdventureStoryPlanGenerationGateway;
import com.dndmaster.adventure.application.prologue.AdventurePrologueApplicationService;
import com.dndmaster.adventure.application.prologue.AdventurePrologueGenerationPort;

@Configuration(proxyBeanMethods = false)
public class AdventureApiConfiguration {

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
            @Value("${adventure.integration.internal-token:local-dev-internal-token}") String token) {
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
            com.dndmaster.adventure.application.scenario.blueprint.CharacterInputTagExtractionPort characterTagExtraction) {
        return new ScenarioPreparationApplicationService(packageRepository, bundleRepository, runtimeOptionCatalogPort,
                characterContextSearch, characterTagExtraction,
                new com.dndmaster.adventure.application.scenario.blueprint.CharacterCreationBlueprintCompiler());
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
            KnowledgeDocumentLookupPort lookupPort) {
        return new RuntimeBindingApplicationService(
                adventureRepository, bundleRepository, packageRepository, runtimeBindingRepository, proposalPort,
                lookupPort);
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
    RuntimePlanningPort runtimePlanningPort() {
        return request -> {
            RuntimeEvidence primaryEvidence = request.evidencePack().storybook().isEmpty()
                    ? (request.evidencePack().rulebook().isEmpty() ? null : request.evidencePack().rulebook().getFirst())
                    : request.evidencePack().storybook().getFirst();
            String scene = primaryEvidence == null
                    ? "서버가 현재 문맥을 바탕으로 장면을 정리했다."
                    : primaryEvidence.excerpt();
            String judgment = "서버가 '" + request.action() + "' 행동을 근거와 함께 정리했다.";
            String narration = primaryEvidence == null
                    ? "근거를 확인한 뒤 응답한다."
                    : "근거를 바탕으로 '" + request.action() + "'에 응답한다.";
            return new RuntimePlan(
                    scene,
                    request.currentContext().npcStateValue().orElse(null),
                    judgment,
                    narration,
                    primaryEvidence == null ? request.activeSourceContext() : new ActiveSourceContext(
                            primaryEvidence.knowledgeDocumentId(), primaryEvidence.extractionVersion(),
                            primaryEvidence.locator(), primaryEvidence.excerpt()),
                    primaryEvidence == null ? List.of() : List.of(primaryEvidence),
                    request.evidencePack().resolution().isEmpty()
                            ? List.of("resolution evidence not prefetched")
                            : List.of());
        };
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
            SessionKnowledgeSetRepository sessionKnowledgeSetRepository) {
        return new RuntimeTurnApplicationService(
                adventureRepository, runtimeBindingRepository, packageRepository, runtimeTurnRepository, runtimeEvidenceSearchPort,
                runtimePlanningPort, narrationSafetyPort, sessionKnowledgeSetRepository);
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
    CombatMapPort combatMapPort() {
        return command -> { /* TODO: implement map validation */ };
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
            GmTurnRepository gmTurnRepository,
            RuntimeTurnRepository runtimeTurnRepository,
            SessionEventRepository sessionEventRepository,
            RuleGuidanceApplicationService guidanceService,
            AdventureCombatApplicationService combatService,
            AdventureScenarioApplicationService scenarioService,
            AuthenticatedPlayerResolver playerResolver) {
        return new AdventureController(
                savedAdventureService, runtimeTurnService, gmTurnRepository, runtimeTurnRepository, sessionEventRepository, guidanceService, combatService, scenarioService, playerResolver);
    }

    @Bean
    RuntimeBindingController runtimeBindingController(
            RuntimeBindingApplicationService service,
            AuthenticatedPlayerResolver playerResolver) {
        return new RuntimeBindingController(service, playerResolver);
    }
}
