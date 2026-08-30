package com.dndmaster.adventure.application.scenario.compilation;

import com.dndmaster.adventure.domain.scenario.CompilationCandidate;
import java.util.List;
import java.util.UUID;

final class NoopCompilationCandidateRepository implements CompilationCandidateRepository {
    @Override public void saveAll(UUID compilationId, List<CompilationCandidate> candidates) {}
    @Override public List<CompilationCandidate> findByCompilationId(UUID compilationId) { return List.of(); }
}
