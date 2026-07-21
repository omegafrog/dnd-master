package com.dndmaster.ruleknowledge.api;

import com.dndmaster.ruleknowledge.application.indexing.*;
import com.dndmaster.ruleknowledge.application.pipeline.RulebookPipelineApplicationService;
import com.dndmaster.ruleknowledge.application.registration.*;
import com.dndmaster.ruleknowledge.application.search.RuleEvidenceSearchApplicationService;
import com.dndmaster.ruleknowledge.domain.index.RulebookIndexingPolicy;
import com.dndmaster.ruleknowledge.infrastructure.extraction.*;
import com.dndmaster.ruleknowledge.infrastructure.persistence.PostgresRulebookIndexRepository;
import com.dndmaster.ruleknowledge.infrastructure.persistence.PostgresRulebookRegistrationRepository;
import com.dndmaster.ruleknowledge.infrastructure.persistence.PgvectorRuleEvidenceSearchRepository;
import com.dndmaster.ruleknowledge.infrastructure.storage.LocalFileSystemRulebookStorage;
import com.dndmaster.ruleknowledge.infrastructure.storage.RulebookStorageProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.util.Map;

import com.dndmaster.ruleknowledge.domain.rulebook.RulebookFormat;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RulebookStorageProperties.class)
public class RuleKnowledgeApiConfiguration {

    @Bean
    RulebookFileStorage rulebookFileStorage(RulebookStorageProperties properties) {
        return new LocalFileSystemRulebookStorage(Path.of(properties.resolveRoot()));
    }

    @Bean
    RulebookContentExtractor rulebookContentExtractor() {
        return new CompositeRulebookContentExtractor(Map.of(
                RulebookFormat.PDF, new PdfRulebookContentExtractor(),
                RulebookFormat.DOCX, new DocxRulebookContentExtractor(),
                RulebookFormat.TXT, new TxtRulebookContentExtractor()));
    }

    @Bean
    RulebookRegistrationRepository registrationRepository(DataSource dataSource) {
        return new PostgresRulebookRegistrationRepository(dataSource);
    }

    @Bean
    RulebookIndexRepository indexRepository(DataSource dataSource) {
        return new PostgresRulebookIndexRepository(dataSource);
    }

    @Bean
    RulebookIndexingPolicy rulebookIndexingPolicy() {
        return new RulebookIndexingPolicy(4000);
    }

    @Bean
    RulebookRegistrationApplicationService rulebookRegistrationApplicationService(
            RulebookFileStorage fileStorage, RulebookContentExtractor contentExtractor) {
        return new RulebookRegistrationApplicationService(fileStorage, contentExtractor);
    }

    @Bean
    PgvectorRuleEvidenceSearchRepository evidenceSearchRepository(DataSource dataSource) {
        return new PgvectorRuleEvidenceSearchRepository(dataSource);
    }

    @Bean
    RulebookIndexingApplicationService indexingApplicationService(
            RulebookIndexRepository indexRepository,
            EmbeddingPort embeddingPort,
            RulebookIndexingPolicy indexingPolicy) {
        return new RulebookIndexingApplicationService(indexRepository, embeddingPort, indexingPolicy);
    }

    @Bean
    RulebookPipelineApplicationService pipelineService(
            RulebookRegistrationApplicationService registrationService,
            RulebookRegistrationRepository registrationRepository,
            RulebookFileStorage fileStorage,
            RulebookContentExtractor contentExtractor,
            RulebookIndexingApplicationService indexingService) {
        return new RulebookPipelineApplicationService(
                registrationService, registrationRepository, fileStorage, contentExtractor, indexingService);
    }

    @Bean
    RuleEvidenceSearchApplicationService evidenceSearchService(
            PgvectorRuleEvidenceSearchRepository searchRepository) {
        return new RuleEvidenceSearchApplicationService(searchRepository);
    }

    @Bean
    RuleKnowledgeController ruleKnowledgeController(
            RulebookPipelineApplicationService pipelineService,
            RulebookRegistrationRepository registrationRepository,
            RuleEvidenceSearchApplicationService evidenceSearchService) {
        return new RuleKnowledgeController(pipelineService, registrationRepository, evidenceSearchService);
    }
}
