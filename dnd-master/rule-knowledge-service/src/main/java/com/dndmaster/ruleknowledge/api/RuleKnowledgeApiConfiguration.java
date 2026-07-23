package com.dndmaster.ruleknowledge.api;

import com.dndmaster.ruleknowledge.application.indexing.*;
import com.dndmaster.ruleknowledge.application.pipeline.RulebookPipelineApplicationService;
import com.dndmaster.ruleknowledge.application.registration.*;
import com.dndmaster.ruleknowledge.application.search.RuleEvidenceSearchApplicationService;
import com.dndmaster.ruleknowledge.infrastructure.extraction.*;
import com.dndmaster.ruleknowledge.infrastructure.ocr.TesseractOcrAdapter;
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
import java.time.Duration;

import com.dndmaster.ruleknowledge.domain.rulebook.RulebookFormat;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RulebookStorageProperties.class)
public class RuleKnowledgeApiConfiguration {

    @Bean
    RulebookFileStorage rulebookFileStorage(RulebookStorageProperties properties) {
        return new LocalFileSystemRulebookStorage(Path.of(properties.resolveRoot()));
    }

    @Bean
    com.dndmaster.ruleknowledge.application.ocr.OcrPort ocrPort(
            @Value("${rule-knowledge.ocr.executable:tesseract}") String executable,
            @Value("${rule-knowledge.ocr.languages:eng,kor}") String languages,
            @Value("${rule-knowledge.ocr.request-timeout:20s}") Duration requestTimeout) {
        return new TesseractOcrAdapter(executable, java.util.Arrays.asList(languages.split(",")), requestTimeout);
    }

    @Bean
    RulebookContentExtractor rulebookContentExtractor(
            com.dndmaster.ruleknowledge.application.ocr.OcrPort ocrPort) {
        return new CompositeRulebookContentExtractor(Map.of(
                RulebookFormat.PDF, new PdfRulebookContentExtractor(ocrPort),
                RulebookFormat.DOCX, new DocxRulebookContentExtractor(),
                RulebookFormat.TXT, new TxtRulebookContentExtractor(),
                RulebookFormat.IMAGE, new ImageRulebookContentExtractor(ocrPort)));
    }

    @Bean
    SourcePreviewExtractor sourcePreviewExtractor(
            com.dndmaster.ruleknowledge.application.ocr.OcrPort ocrPort) {
        return new CompositeSourcePreviewExtractor(Map.of(
                RulebookFormat.PDF, new PdfSourcePreviewExtractor(ocrPort),
                RulebookFormat.DOCX, new DocxSourcePreviewExtractor(),
                RulebookFormat.TXT, new TxtSourcePreviewExtractor(),
                RulebookFormat.IMAGE, new ImageSourcePreviewExtractor(ocrPort)));
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
