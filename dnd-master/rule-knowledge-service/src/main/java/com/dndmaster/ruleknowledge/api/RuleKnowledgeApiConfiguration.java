package com.dndmaster.ruleknowledge.api;

import com.dndmaster.ruleknowledge.application.indexing.*;
import com.dndmaster.ruleknowledge.application.pipeline.RulebookPipelineApplicationService;
import com.dndmaster.ruleknowledge.application.registration.*;
import com.dndmaster.ruleknowledge.application.search.RuleEvidenceSearchApplicationService;
import com.dndmaster.ruleknowledge.infrastructure.extraction.*;
import com.dndmaster.ruleknowledge.infrastructure.persistence.PostgresRulebookIndexRepository;
import com.dndmaster.ruleknowledge.infrastructure.persistence.PostgresRulebookRegistrationRepository;
import com.dndmaster.ruleknowledge.application.search.RuleEvidenceSearchPort;
import com.dndmaster.ruleknowledge.infrastructure.persistence.PgvectorRuleEvidenceSearchRepository;
import com.dndmaster.ruleknowledge.infrastructure.storage.LocalFileSystemRulebookStorage;
import com.dndmaster.ruleknowledge.infrastructure.storage.RulebookStorageProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
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
    SourcePreviewExtractor sourcePreviewExtractor() {
        return new CompositeSourcePreviewExtractor(Map.of(
                RulebookFormat.PDF, new PdfSourcePreviewExtractor(),
                RulebookFormat.DOCX, new DocxSourcePreviewExtractor(),
                RulebookFormat.TXT, new TxtSourcePreviewExtractor()));
    }

    @Bean
    RulebookRegistrationRepository registrationRepository(DataSource dataSource, ObjectMapper objectMapper) {
        return new PostgresRulebookRegistrationRepository(dataSource, objectMapper);
    }

    @Bean
    RulebookIndexRepository indexRepository(DataSource dataSource) {
        return new PostgresRulebookIndexRepository(dataSource);
    }

    @Bean
    RulebookRegistrationApplicationService rulebookRegistrationApplicationService(
            RulebookFileStorage fileStorage, RulebookContentExtractor contentExtractor) {
        return new RulebookRegistrationApplicationService(fileStorage, contentExtractor);
    }

    @Bean
    RuleEvidenceSearchPort evidenceSearchRepository(DataSource dataSource) {
        return new PgvectorRuleEvidenceSearchRepository(dataSource);
    }

    @Bean
    RulebookIndexingApplicationService indexingApplicationService(
            RulebookIndexRepository indexRepository,
            EmbeddingPort embeddingPort,
            StructureDetectionPort structureDetectionPort) {
        return new RulebookIndexingApplicationService(indexRepository, embeddingPort, structureDetectionPort, 4000);
    }

    @Bean
    RulebookPipelineApplicationService pipelineService(
            RulebookRegistrationApplicationService registrationService,
            RulebookRegistrationRepository registrationRepository,
            RulebookFileStorage fileStorage,
            RulebookContentExtractor contentExtractor,
            SourcePreviewExtractor sourcePreviewExtractor,
            RulebookIndexingApplicationService indexingService,
            @Value("${rule-knowledge.embedding-dimension:1024}") int embeddingDimension) {
        return new RulebookPipelineApplicationService(
                registrationService,
                registrationRepository,
                fileStorage,
                contentExtractor,
                sourcePreviewExtractor,
                indexingService,
                embeddingDimension);
    }

    @Bean
    RuleEvidenceSearchApplicationService evidenceSearchService(
            RuleEvidenceSearchPort searchRepository,
            EmbeddingPort embeddingPort,
            @Value("${rule-knowledge.embedding-model:qwen3-embedding:0.6b}") String embeddingModel,
            @Value("${rule-knowledge.embedding-dimension:1024}") int embeddingDimension) {
        return new RuleEvidenceSearchApplicationService(searchRepository, embeddingPort, embeddingModel, embeddingDimension);
    }

    @Bean
    RuleKnowledgeController ruleKnowledgeController(
            RulebookPipelineApplicationService pipelineService,
            RulebookRegistrationRepository registrationRepository,
            RuleEvidenceSearchApplicationService evidenceSearchService,
            ObjectMapper objectMapper) {
        return new RuleKnowledgeController(
                pipelineService, registrationRepository, evidenceSearchService, objectMapper);
    }
}
