package com.dndmaster.adventure.api;

import com.dndmaster.adventure.application.combat.*;
import com.dndmaster.adventure.application.knowledge.*;
import com.dndmaster.adventure.application.guidance.*;
import com.dndmaster.adventure.application.progress.*;
import com.dndmaster.adventure.application.ruleset.*;
import com.dndmaster.adventure.application.saved.*;
import com.dndmaster.adventure.application.scenario.*;
import com.dndmaster.adventure.domain.adventure.Adventure;
import com.dndmaster.adventure.domain.inquiry.RulebookId;
import com.dndmaster.adventure.domain.scenario.ScenarioSource;
import com.dndmaster.adventure.infrastructure.persistence.PostgresAdventureRepository;
import com.dndmaster.adventure.infrastructure.persistence.PostgresSessionKnowledgeSetRepository;
import com.dndmaster.adventure.infrastructure.integration.CrossContextHttpKnowledgeDocumentLookupGateway;
import com.dndmaster.adventure.infrastructure.integration.CrossContextHttpRuleIntentClassificationGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.List;

@Configuration(proxyBeanMethods = false)
public class AdventureApiConfiguration {

    @Bean
    AdventureRepository adventureRepository(DataSource dataSource) {
        return new PostgresAdventureRepository(dataSource);
    }

    @Bean
    SessionKnowledgeSetRepository sessionKnowledgeSetRepository(DataSource dataSource) {
        return new PostgresSessionKnowledgeSetRepository(dataSource);
    }

    @Bean
    ScenarioStoragePort scenarioStoragePort() {
        return upload -> new ScenarioSource(
                upload.originalFilename(), upload.originalFilename(), "hash-" + upload.content().length);
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
    AdventureScenarioApplicationService scenarioApplicationService(
            AdventureScenarioRepository repository,
            ScenarioStoragePort storagePort,
            ScenarioPreparationPort preparationPort) {
        return new AdventureScenarioApplicationService(repository, storagePort, preparationPort);
    }

    @Bean
    SavedAdventureApplicationService savedAdventureApplicationService(AdventureRepository repository) {
        return new SavedAdventureApplicationService(repository);
    }

    @Bean
    KnowledgeDocumentLookupPort knowledgeDocumentLookupPort(ObjectMapper objectMapper) {
        return new CrossContextHttpKnowledgeDocumentLookupGateway(
                HttpClient.newHttpClient(),
                URI.create("http://127.0.0.1:18083/"),
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
    AdventureReadinessPort adventureReadinessPort() {
        return adventure -> new AdventureReadiness(true, true, true);
    }

    @Bean
    RuleIntentClassificationPort ruleIntentClassificationPort(ObjectMapper objectMapper) {
        return new CrossContextHttpRuleIntentClassificationGateway(
                HttpClient.newHttpClient(),
                URI.create("http://127.0.0.1:18087/"),
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

    @Bean
    AdventureProgressApplicationService progressApplicationService(
            AdventureRepository repository,
            AdventureReadinessPort readinessPort,
            AiGameMasterPort aiGameMasterPort) {
        return new AdventureProgressApplicationService(repository, readinessPort, aiGameMasterPort);
    }

    @Bean
    RuleSetSearchScopePort ruleSetSearchScopePort(
            AdventureRepository adventureRepository,
            SessionKnowledgeSetRepository sessionKnowledgeSetRepository) {
        return (adventureId, ruleSetId, owner) -> adventureRepository.findById(adventureId)
                .filter(adventure -> adventure.ownerPlayerId().equals(owner))
                .filter(adventure -> adventure.ruleSetId().equals(ruleSetId))
                .flatMap(adventure -> sessionKnowledgeSetRepository.findBySessionId(adventure.sessionId()))
                .filter(set -> !set.knowledgeDocumentIds().isEmpty())
                .map(set -> new RuleSearchScope(true, set.knowledgeDocumentIds()))
                .orElseGet(() -> new RuleSearchScope(false, List.of()));
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
            AdventureProgressApplicationService progressService,
            RuleGuidanceApplicationService guidanceService,
            AdventureCombatApplicationService combatService,
            AdventureScenarioApplicationService scenarioService) {
        return new AdventureController(savedAdventureService, progressService, guidanceService, combatService, scenarioService);
    }
}
