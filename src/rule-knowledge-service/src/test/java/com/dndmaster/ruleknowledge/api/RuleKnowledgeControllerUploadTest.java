package com.dndmaster.ruleknowledge.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dndmaster.ruleknowledge.application.pipeline.BatchRulebookUploadApplicationService;
import com.dndmaster.ruleknowledge.application.pipeline.RulebookPipelineApplicationService;
import com.dndmaster.ruleknowledge.application.pipeline.RulebookProcessingResult;
import com.dndmaster.ruleknowledge.application.pipeline.UploadRulebookCommand;
import com.dndmaster.ruleknowledge.application.registration.RulebookRegistrationRepository;
import com.dndmaster.ruleknowledge.application.search.RuleEvidenceSearchApplicationService;
import com.dndmaster.ruleknowledge.domain.rulebook.ProcessingStatus;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookId;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RuleKnowledgeControllerUploadTest {
    @Test
    void batchUploadAcceptsStorybookDocumentsAndMatchingFiles() throws Exception {
        RulebookPipelineApplicationService pipelineService = mock(RulebookPipelineApplicationService.class);
        RulebookRegistrationRepository registrationRepository = mock(RulebookRegistrationRepository.class);
        RuleEvidenceSearchApplicationService evidenceSearchService = mock(RuleEvidenceSearchApplicationService.class);
        RuleKnowledgeController controller = new RuleKnowledgeController(
                pipelineService,
                registrationRepository,
                evidenceSearchService,
                new com.fasterxml.jackson.databind.ObjectMapper());
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();

        when(pipelineService.process(any())).thenReturn(new RulebookProcessingResult(
                RulebookId.generate(), ProcessingStatus.QUEUED, List.of()));

        UUID ownerId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        mockMvc.perform(multipart("/api/v1/rulebooks")
                        .file(new MockMultipartFile(
                                "documents",
                                "documents.json",
                                "application/json",
                                """
                                        [{"idempotencyKey":"op-1","documentType":"STORYBOOK","originalFilename":"story.pdf"}]
                                        """.getBytes(StandardCharsets.UTF_8)))
                        .file(new MockMultipartFile(
                                "files",
                                "story.pdf",
                                "application/pdf",
                                "rules".getBytes(StandardCharsets.UTF_8)))
                        .param("ownerPlayerId", ownerId.toString())
                        .header("Authorization", "Bearer " + ownerId))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.documents[0].status").value("ACCEPTED"))
                .andExpect(jsonPath("$.documents[0].originalFilename").value("story.pdf"))
                .andExpect(jsonPath("$.documents[0].documentType").value("STORYBOOK"));

        verify(pipelineService).process(any(UploadRulebookCommand.class));
    }
}
