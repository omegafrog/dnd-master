package com.dndmaster.ruleknowledge.application.extraction;

import com.dndmaster.ruleknowledge.domain.extraction.DocumentExtractionResult;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookFormat;

public interface DocumentExtractionPort {
    DocumentExtractionResult extract(RulebookFormat format, byte[] content);
}
