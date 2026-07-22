package com.dndmaster.ruleknowledge.application.pipeline;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public final class RulebookProcessingWorker {
    private static final Logger log = Logger.getLogger(RulebookProcessingWorker.class.getName());

    private final RulebookPipelineApplicationService pipelineService;

    public RulebookProcessingWorker(RulebookPipelineApplicationService pipelineService) {
        this.pipelineService = pipelineService;
    }

    @Scheduled(fixedDelayString = "${rule-knowledge.processing-poll-delay-ms:5000}")
    public void processQueuedDocuments() {
        try {
            pipelineService.processPending();
        } catch (RuntimeException exception) {
            log.log(Level.WARNING, "queued document processing failed", exception);
        }
    }
}
