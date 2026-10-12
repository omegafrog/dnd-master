package com.dndmaster.adventure.application.knowledge;

import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.domain.scenario.StructuredSpellDefinition;
import java.util.List;

public interface RulebookActionDefinitionLookupPort {
    List<StructuredSpellDefinition> find(KnowledgeDocumentId documentId, long registrationVersion);
}
