package com.dndmaster.aigamemaster.configuration;

import com.dndmaster.aigamemaster.retrieval.RetrievalEvaluationArtifactStore;
import com.dndmaster.aigamemaster.retrieval.RetrievalEvaluationCorpusLoader;
import com.dndmaster.aigamemaster.retrieval.RetrievalEvaluationIdentity;
import com.dndmaster.aigamemaster.retrieval.RetrievalEvaluationPort;
import com.dndmaster.aigamemaster.retrieval.RetrievalEvaluationRunner;
import com.dndmaster.aigamemaster.retrieval.RetrievalQualityGate;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("retrieval-evaluation")
public class RetrievalEvaluationTaskConfiguration {
    @Bean
    ApplicationRunner retrievalEvaluationTask(
            RetrievalEvaluationPort port,
            ObjectMapper mapper,
            @Value("${retrieval.evaluation.artifact-directory:build/retrieval-evaluation}") Path artifactDirectory,
            @Value("${retrieval.evaluation.corpus-digest:unreleased}") String corpusDigest,
            @Value("${retrieval.evaluation.embedding-model:unreleased}") String embeddingModel,
            @Value("${retrieval.evaluation.index-version:unreleased}") String indexVersion,
            @Value("${retrieval.evaluation.service-version:unreleased}") String serviceVersion,
            @Value("${retrieval.evaluation.configuration-digest:unreleased}") String configurationDigest) {
        return args -> {
            var corpus = RetrievalEvaluationCorpusLoader.load(
                    RetrievalEvaluationTaskConfiguration.class.getResourceAsStream("/retrieval-evaluation-corpus.json"), mapper);
            var report = new RetrievalEvaluationRunner().runReport(corpus, port);
            new RetrievalEvaluationArtifactStore(mapper).write(artifactDirectory, report,
                    new RetrievalEvaluationIdentity(corpusDigest, embeddingModel, indexVersion,
                            serviceVersion, configurationDigest));
            RetrievalQualityGate.evaluate(corpus, report).assertPassed();
        };
    }
}
