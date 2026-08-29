package com.dndmaster.gmeval.domain;
public record HardConstraintResult(String expectationId, HardStatus status, String reason, String evidence) {
    public HardConstraintResult { if (expectationId == null || status == null) throw new IllegalArgumentException("result required");
        if (status == HardStatus.FAIL && evidence == null) throw new IllegalArgumentException("failure evidence may be empty but not null");
        if (status == HardStatus.UNEVALUATED && (reason == null || reason.isBlank())) throw new IllegalArgumentException("unevaluated reason required"); }
    public static HardConstraintResult pass(String id, String evidence) { return new HardConstraintResult(id, HardStatus.PASS, "satisfied", evidence == null ? "" : evidence); }
    public static HardConstraintResult fail(String id, String reason, String evidence) { return new HardConstraintResult(id, HardStatus.FAIL, reason, evidence); }
    public static HardConstraintResult unevaluated(String id, String reason) { return new HardConstraintResult(id, HardStatus.UNEVALUATED, reason, ""); }
}
