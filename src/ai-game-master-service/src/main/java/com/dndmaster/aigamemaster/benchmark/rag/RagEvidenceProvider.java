package com.dndmaster.aigamemaster.benchmark.rag;

import com.dndmaster.aigamemaster.benchmark.GmBenchmarkCase;
import java.util.List;

@FunctionalInterface
public interface RagEvidenceProvider { List<String> evidence(GmBenchmarkCase benchmarkCase); }
