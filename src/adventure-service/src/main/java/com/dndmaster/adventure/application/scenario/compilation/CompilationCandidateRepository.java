package com.dndmaster.adventure.application.scenario.compilation;

import com.dndmaster.adventure.domain.scenario.CompilationCandidate;
import java.util.List;
import java.util.UUID;

public interface CompilationCandidateRepository {
    void saveAll(UUID compilationId, List<CompilationCandidate> candidates);
    List<CompilationCandidate> findByCompilationId(UUID compilationId);
}
