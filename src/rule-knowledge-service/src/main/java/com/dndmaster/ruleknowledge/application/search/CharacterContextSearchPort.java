package com.dndmaster.ruleknowledge.application.search;

import com.dndmaster.ruleknowledge.domain.rulebook.*;
import java.util.List;

public interface CharacterContextSearchPort {
    List<CharacterContextSearchHit> search(
            OwnerPlayerId owner, DocumentType type, List<CharacterContextDocumentScope> scope, float[] queryEmbedding);

    default List<CharacterContextSearchHit> search(
            OwnerPlayerId owner, DocumentType type, List<CharacterContextDocumentScope> scope,
            float[] queryEmbedding, List<String> chapterHints) {
        return search(owner, type, scope, queryEmbedding);
    }
}
