package com.dndmaster.ruleknowledge.application.extraction;

import com.dndmaster.ruleknowledge.domain.document.normalized.NormalizedDocument;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookFormat;

/** Parser-neutral boundary consumed by hierarchy resolution. */
public interface NormalizedDocumentExtractionPort {
    NormalizedDocument extractNormalized(RulebookFormat format, byte[] content);
}
