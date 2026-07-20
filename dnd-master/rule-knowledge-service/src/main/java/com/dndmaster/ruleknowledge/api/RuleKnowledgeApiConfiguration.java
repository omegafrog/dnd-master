package com.dndmaster.ruleknowledge.api;

import com.dndmaster.ruleknowledge.application.registration.RulebookRegistrationApplicationService;
import com.dndmaster.ruleknowledge.application.registration.RulebookContentExtractor;
import com.dndmaster.ruleknowledge.application.registration.RulebookFileStorage;
import com.dndmaster.ruleknowledge.application.registration.StoredRulebookFile;
import com.dndmaster.ruleknowledge.domain.rulebook.ExtractionResult;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookFormat;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class RuleKnowledgeApiConfiguration {

    @Bean
    RulebookFileStorage rulebookFileStorage() {
        return new RulebookFileStorage() {
            @Override
            public StoredRulebookFile store(RulebookId id, byte[] content) {
                // TODO: implement file storage
                throw new UnsupportedOperationException("file storage not yet implemented");
            }

            @Override
            public byte[] read(StoredRulebookFile file) {
                // TODO: implement file reading
                throw new UnsupportedOperationException("file reading not yet implemented");
            }
        };
    }

    @Bean
    RulebookContentExtractor rulebookContentExtractor() {
        return (format, content) -> {
            // TODO: implement content extraction
            throw new UnsupportedOperationException("content extraction not yet implemented");
        };
    }

    @Bean
    RulebookRegistrationApplicationService rulebookRegistrationApplicationService(
            RulebookFileStorage fileStorage, RulebookContentExtractor contentExtractor) {
        return new RulebookRegistrationApplicationService(fileStorage, contentExtractor);
    }

    @Bean
    RuleKnowledgeController ruleKnowledgeController(
            RulebookRegistrationApplicationService registrationService) {
        return new RuleKnowledgeController(registrationService);
    }
}
