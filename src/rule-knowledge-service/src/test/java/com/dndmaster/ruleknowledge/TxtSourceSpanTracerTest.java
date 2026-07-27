package com.dndmaster.ruleknowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dndmaster.ruleknowledge.domain.rulebook.SourceSpan;
import com.dndmaster.ruleknowledge.infrastructure.extraction.TxtSourceSpanTracer;
import java.util.List;
import org.junit.jupiter.api.Test;

class TxtSourceSpanTracerTest {

    @Test
    void tracesTxtLinesWithExactOffsetsAndBlankLines() {
        TxtSourceSpanTracer tracer = new TxtSourceSpanTracer();

        List<SourceSpan> spans = tracer.trace("alpha\n\nbeta\r\ngamma");

        assertEquals(List.of(
                new SourceSpan(1, 0, 5, "alpha", "line 1 chars 0-5"),
                new SourceSpan(2, 6, 6, "", "line 2 chars 6-6"),
                new SourceSpan(3, 7, 11, "beta", "line 3 chars 7-11"),
                new SourceSpan(4, 13, 18, "gamma", "line 4 chars 13-18")), spans);
    }
}
