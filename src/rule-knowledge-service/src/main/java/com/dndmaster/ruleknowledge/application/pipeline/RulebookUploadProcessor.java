package com.dndmaster.ruleknowledge.application.pipeline;

public interface RulebookUploadProcessor {
    RulebookProcessingResult process(UploadRulebookCommand command);
}
