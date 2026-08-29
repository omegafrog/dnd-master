package com.dndmaster.gmeval.domain;
public final class PairwiseEvaluationService {
    private final AbsoluteEvaluationService absolute; private final PairwiseJudgePort judge;
    public PairwiseEvaluationService(PairwiseJudgePort judge) { this(new AbsoluteEvaluationService(), judge); }
    public PairwiseEvaluationService(AbsoluteEvaluationService absolute, PairwiseJudgePort judge) { this.absolute = absolute == null ? new AbsoluteEvaluationService() : absolute; this.judge = judge; }
    public PairwiseEvalResult compare(EvalCase c, String a, String b) { return compare(c, new PairwiseResponse(c.caseId(), c.schemaVersion(), a), new PairwiseResponse(c.caseId(), c.schemaVersion(), b)); }
    public PairwiseEvalResult compare(EvalCase c, PairwiseResponse a, PairwiseResponse b) {
        if (c == null || a == null || b == null || !c.caseId().equals(a.caseId()) || !c.caseId().equals(b.caseId()) || c.schemaVersion() != a.schemaVersion() || c.schemaVersion() != b.schemaVersion()) throw new IllegalArgumentException("pairwise responses must match case identity and version");
        EvalResult ar = absolute.evaluate(c, a.response()), br = absolute.evaluate(c, b.response());
        if (judge == null) return new PairwiseEvalResult(null, java.util.List.of(), null, null, ar, br, "pairwise judge not configured");
        try { PairwiseJudgeResponse r = PairwiseJudgeResponseValidator.validate(c.rubrics(), judge.compare(new PairwiseJudgeRequest(c, a.response(), b.response(), c.rubrics()))); return new PairwiseEvalResult(r.winner(), r.preferences(), r.reason(), r.evidence(), ar, br, null); }
        catch (RuntimeException e) { return new PairwiseEvalResult(null, java.util.List.of(), null, null, ar, br, e.getMessage() == null ? "invalid pairwise judge response" : e.getMessage()); }
    }
}
