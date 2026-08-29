package com.dndmaster.adventure.application.runtime;

import java.util.List;
import java.util.Objects;

/** Orchestrates candidate generation, admission, judging, selection, and audit. */
public final class BestOfNPlanningCoordinator {
    private final PlanCandidatePort candidatePort;
    private final PlanJudgePort judgePort;
    private final CandidateHardFilter hardFilter;
    private final PlanSelectionPolicy selectionPolicy;
    private final PlanAuditPort auditPort;

    public BestOfNPlanningCoordinator(PlanCandidatePort candidatePort, PlanJudgePort judgePort,
                                      PlanAuditPort auditPort) {
        this(candidatePort, judgePort, new CandidateHardFilter(), new PlanSelectionPolicy(), auditPort);
    }

    public BestOfNPlanningCoordinator(PlanCandidatePort candidatePort, PlanJudgePort judgePort,
                                      CandidateHardFilter hardFilter, PlanSelectionPolicy selectionPolicy,
                                      PlanAuditPort auditPort) {
        this.candidatePort = Objects.requireNonNull(candidatePort);
        this.judgePort = Objects.requireNonNull(judgePort);
        this.hardFilter = Objects.requireNonNull(hardFilter);
        this.selectionPolicy = Objects.requireNonNull(selectionPolicy);
        this.auditPort = Objects.requireNonNull(auditPort);
    }

    public PlanSelection plan(PlanningContext context, int requestedCount, boolean simpleTurn) {
        int count = PlanningContext.boundedCandidateCount(requestedCount, simpleTurn);
        List<PlanCandidate> generated = List.copyOf(candidatePort.generate(context, count));
        CandidateHardFilter.FilterResult filtered = hardFilter.filter(generated, context);
        if (filtered.valid().isEmpty()) {
            auditPort.append(new PlanSelectionAudit(count, generated.stream().map(PlanCandidate::candidateId).toList(),
                    filtered.rejected(), List.of(), "", "no valid candidates"));
            throw new IllegalStateException("no valid turn plan candidates");
        }
        List<PlanSelection.Score> scores;
        try {
            scores = List.copyOf(judgePort.judge(filtered.valid(), context));
        } catch (RuntimeException failure) {
            scores = List.of();
        }
        PlanSelection selected = selectionPolicy.select(filtered.valid(), scores);
        auditPort.append(new PlanSelectionAudit(count, generated.stream().map(PlanCandidate::candidateId).toList(),
                filtered.rejected(), selected.evaluations(), selected.selected().candidateId(), scores.isEmpty() ? "judge fallback" : ""));
        return selected;
    }
}
