package com.dndmaster.ruleknowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.ruleknowledge.infrastructure.extraction.TxtRulebookContentExtractor;
import com.dndmaster.ruleknowledge.domain.rulebook.ExtractionFailure;
import com.dndmaster.ruleknowledge.domain.rulebook.ExtractionResult;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class TxtRulebookContentExtractorTest {

    private final TxtRulebookContentExtractor extractor = new TxtRulebookContentExtractor();

    @Test
    void extractsNormalTxtContent() {
        byte[] content = "Core movement rules.\nCast spells.".getBytes(StandardCharsets.UTF_8);

        ExtractionResult result = extractor.extract(content);

        assertEquals(ExtractionResult.success("Core movement rules.\nCast spells.").content(), result.content());
    }

    @Test
    void stripsUtf8Bom() {
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] text = "Hello".getBytes(StandardCharsets.UTF_8);
        byte[] content = new byte[bom.length + text.length];
        System.arraycopy(bom, 0, content, 0, bom.length);
        System.arraycopy(text, 0, content, bom.length, text.length);

        ExtractionResult result = extractor.extract(content);

        assertTrue(result.content().isPresent());
        assertEquals("Hello", result.content().get());
    }

    @Test
    void rejectsBlankContent() {
        byte[] content = "   \n  ".getBytes(StandardCharsets.UTF_8);

        ExtractionResult result = extractor.extract(content);

        assertEquals(ExtractionFailure.UNPROCESSABLE, result.failure().orElseThrow());
    }

    @Test
    void rejectsContentWithNullChars() {
        byte[] content = "Hello\0World".getBytes(StandardCharsets.UTF_8);

        ExtractionResult result = extractor.extract(content);

        assertEquals(ExtractionFailure.UNPROCESSABLE, result.failure().orElseThrow());
    }
}
