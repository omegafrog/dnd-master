package com.dndmaster.ruleknowledge.application.registration;

import com.dndmaster.ruleknowledge.domain.rulebook.ExtractionResult;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookFormat;

public interface RulebookContentExtractor {
    ExtractionResult extract(RulebookFormat format, byte[] content);
}
