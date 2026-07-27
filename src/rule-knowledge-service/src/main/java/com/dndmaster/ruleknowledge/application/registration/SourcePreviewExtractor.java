package com.dndmaster.ruleknowledge.application.registration;

import com.dndmaster.ruleknowledge.domain.rulebook.SourcePreviewResult;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookFormat;

public interface SourcePreviewExtractor {
    SourcePreviewResult preview(RulebookFormat format, byte[] content);
}
