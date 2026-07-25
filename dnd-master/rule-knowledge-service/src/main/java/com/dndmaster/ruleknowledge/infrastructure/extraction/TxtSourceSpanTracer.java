package com.dndmaster.ruleknowledge.infrastructure.extraction;

import com.dndmaster.ruleknowledge.domain.rulebook.SourceSpan;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class TxtSourceSpanTracer {
    public List<SourceSpan> trace(String content) {
        String text = Objects.requireNonNull(content, "content must not be null");
        if (text.isEmpty()) {
            return List.of();
        }

        List<SourceSpan> spans = new ArrayList<>();
        int lineStart = 0;
        int lineNumber = 1;
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (current == '\r' || current == '\n') {
                int currentLine = lineNumber++;
                spans.add(new SourceSpan(currentLine, lineStart, index, text.substring(lineStart, index), locator(currentLine, lineStart, index)));
                if (current == '\r' && index + 1 < text.length() && text.charAt(index + 1) == '\n') {
                    index++;
                }
                lineStart = index + 1;
            }
        }

        if (lineStart < text.length()) {
            spans.add(new SourceSpan(lineNumber, lineStart, text.length(), text.substring(lineStart), locator(lineNumber, lineStart, text.length())));
        } else if (text.charAt(text.length() - 1) == '\n' || text.charAt(text.length() - 1) == '\r') {
            spans.add(new SourceSpan(lineNumber, text.length(), text.length(), "", locator(lineNumber, text.length(), text.length())));
        }

        return List.copyOf(spans);
    }

    private static String locator(int lineNumber, int startInclusive, int endExclusive) {
        return "line " + lineNumber + " chars " + startInclusive + "-" + endExclusive;
    }
}
