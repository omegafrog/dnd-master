package com.dndmaster.ruleknowledge.infrastructure.extraction;

import com.dndmaster.ruleknowledge.domain.rulebook.ExtractionFailure;
import com.dndmaster.ruleknowledge.domain.rulebook.ExtractionResult;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class TxtRulebookContentExtractor implements CompositeRulebookContentExtractor.FormatExtractor {
    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    @Override
    public ExtractionResult extract(byte[] content) {
        Objects.requireNonNull(content, "content must not be null");
        byte[] stripped = stripBom(content);
        String text = new String(stripped, StandardCharsets.UTF_8);
        if (text.isBlank()) {
            return ExtractionResult.failed(ExtractionFailure.UNPROCESSABLE);
        }
        if (text.chars().anyMatch(c -> c == 0)) {
            return ExtractionResult.failed(ExtractionFailure.UNPROCESSABLE);
        }
        return ExtractionResult.success(text);
    }

    private static byte[] stripBom(byte[] input) {
        if (input.length >= 3 && input[0] == UTF8_BOM[0] && input[1] == UTF8_BOM[1] && input[2] == UTF8_BOM[2]) {
            byte[] result = new byte[input.length - 3];
            System.arraycopy(input, 3, result, 0, result.length);
            return result;
        }
        return input;
    }
}
