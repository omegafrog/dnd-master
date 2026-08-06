package com.dndmaster.aigamemaster.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;

public final class GmBenchmarkCorpusLoader {
    private GmBenchmarkCorpusLoader() {}

    public static GmBenchmarkCorpus load(InputStream input, ObjectMapper mapper) throws IOException {
        if (input == null) throw new IllegalArgumentException("benchmark corpus missing");
        GmBenchmarkCorpus corpus = mapper.readValue(input, GmBenchmarkCorpus.class);
        if (corpus.cases().size() != 30) throw new IllegalArgumentException("baseline corpus must contain 30 cases");
        return corpus;
    }
}
