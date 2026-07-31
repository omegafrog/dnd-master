package com.dndmaster.adventure.api;

import com.dndmaster.adventure.application.campaign.CampaignPlanRepository;
import com.dndmaster.adventure.application.campaign.CampaignPlanningApplicationService;
import com.dndmaster.adventure.application.knowledge.KnowledgeDocumentLookupPort;
import com.dndmaster.adventure.application.knowledge.SessionKnowledgeSetRepository;
import com.dndmaster.adventure.application.scenario.compilation.CharacterContextSearchPort;
import com.dndmaster.adventure.application.session.AdventureSessionRepository;
import com.dndmaster.adventure.application.session.CharacterSheetOwnershipPort;
import com.dndmaster.adventure.infrastructure.persistence.PostgresCampaignPlanRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class CampaignPlanConfiguration {
    @Bean
    CampaignPlanRepository campaignPlanRepository(DataSource dataSource, ObjectMapper objectMapper) {
        return new PostgresCampaignPlanRepository(dataSource, objectMapper);
    }

    @Bean
    CampaignPlanningApplicationService campaignPlanningApplicationService(
            AdventureSessionRepository sessionRepository,
            SessionKnowledgeSetRepository knowledgeSetRepository,
            KnowledgeDocumentLookupPort documentLookup,
            CharacterSheetOwnershipPort characterSheetOwnership,
            CharacterContextSearchPort contextSearch,
            CampaignPlanRepository planRepository) {
        return new CampaignPlanningApplicationService(
                sessionRepository,
                knowledgeSetRepository,
                documentLookup,
                characterSheetOwnership,
                contextSearch,
                planRepository);
    }
}
