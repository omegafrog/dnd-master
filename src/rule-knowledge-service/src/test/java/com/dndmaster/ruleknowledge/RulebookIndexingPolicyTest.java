package com.dndmaster.ruleknowledge;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.ruleknowledge.domain.index.RulebookChunk;
import com.dndmaster.ruleknowledge.domain.index.RulebookIndexingPolicy;
import com.dndmaster.ruleknowledge.domain.rulebook.ExtractionResult;
import com.dndmaster.ruleknowledge.domain.rulebook.FileSize;
import com.dndmaster.ruleknowledge.domain.rulebook.OwnerPlayerId;
import com.dndmaster.ruleknowledge.domain.rulebook.Rulebook;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookFormat;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RulebookIndexingPolicyTest {
    @Test
    void doesNotSplitInsideWordWhenPdfInsertedNewlineBetweenLetters() {
        Rulebook rulebook = Rulebook.acceptUpload(
                RulebookId.generate(), new OwnerPlayerId(UUID.randomUUID()), RulebookFormat.PDF,
                new FileSize(1));
        String content = "prefix\n" + "x".repeat(38) + "sa\nnding on the wrong portion\n" + "tail";
        rulebook.recordExtraction(ExtractionResult.success(content));

        var chunks = new RulebookIndexingPolicy(45).createChunks(rulebook);
        assertFalse(chunks.stream().anyMatch(chunk -> chunk.content().endsWith("sa")));
        assertFalse(chunks.stream().anyMatch(chunk -> chunk.content().startsWith("nding")));
    }
}
