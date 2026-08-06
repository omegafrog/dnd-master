package com.dndmaster.aigamemaster.configuration;

import com.dndmaster.aigamemaster.retrieval.HttpRuleRetrievalAdapter;
import com.dndmaster.aigamemaster.retrieval.HttpStoryRetrievalAdapter;
import com.dndmaster.aigamemaster.retrieval.RetrievalEvaluationPort;
import com.dndmaster.aigamemaster.retrieval.RetrievalEvaluationRouter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RetrievalEvaluationConfiguration {
    @Bean
    RetrievalEvaluationPort retrievalEvaluationPort(
            @Value("${retrieval.rule-knowledge-base-url:http://127.0.0.1:8080}") String baseUrl,
            ObjectMapper mapper) {
        return new RetrievalEvaluationRouter(new HttpRuleRetrievalAdapter(baseUrl, mapper), new HttpStoryRetrievalAdapter(baseUrl, mapper));
    }
}
