package com.dndmaster.ruleknowledge.configuration;

import com.dndmaster.ruleknowledge.application.extraction.DocumentExtractionPort;
import com.dndmaster.ruleknowledge.infrastructure.extraction.DoclingDocumentExtractionAdapter;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
public class DoclingConfiguration {
    @Bean
    DocumentExtractionPort documentExtractionPort(
            ObjectMapper objectMapper,
            @Value("${rule-knowledge.docling.base-url:http://127.0.0.1:8099}") String baseUrl,
            @Value("${rule-knowledge.docling.request-timeout:60s}") Duration timeout) {
        return new DoclingDocumentExtractionAdapter(objectMapper, baseUrl, timeout);
    }
}
