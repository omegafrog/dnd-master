package com.dndmaster.gmeval.application;

import com.dndmaster.gmeval.domain.*;
import com.dndmaster.gmeval.infrastructure.JsonlEvalDatasetLoader;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

/** Orchestrates one pinned dataset run; it owns aggregation, not evaluation semantics. */
public final class EvalRunner {
    private final JsonlEvalDatasetLoader loader;
    private final AbsoluteEvaluationService absolute;
    private final PairwiseEvaluationService pairwise;
    public EvalRunner(AbsoluteEvaluationService absolute, PairwiseEvaluationService pairwise) {
        this.loader = new JsonlEvalDatasetLoader(); this.absolute = absolute == null ? new AbsoluteEvaluationService() : absolute; this.pairwise = pairwise;
    }
    public EvalRunReport run(Path dataset, EvalRunConfiguration config, Map<String,String> responses) {
        return run(loader.load(dataset), config, (c, ignored) -> new GeneratedResponse(Objects.requireNonNull(responses.get(c.caseId()), "response missing for " + c.caseId()), "supplied"), null);
    }
    public EvalRunReport run(Path dataset, EvalRunConfiguration config, ResponseGeneratorPort generator, Path reportPath) {
        EvalRunReport report = run(loader.load(dataset), config, generator, null);
        if (reportPath != null) new JsonEvalReportWriter().write(report, reportPath);
        return report;
    }
    public EvalRunReport run(List<EvalCase> cases, EvalRunConfiguration config, ResponseGeneratorPort generator, Map<String, PairwiseResponse> pairwiseResponses) {
        if (cases == null || cases.isEmpty()) throw new IllegalArgumentException("dataset must not be empty");
        if (generator == null) throw new IllegalArgumentException("response generator required");
        List<EvalRunReport.CaseReport> reports = new ArrayList<>();
        for (EvalCase c : cases) { GeneratedResponse generated = generator.generate(c, config); EvalResult result = absolute.evaluate(c, generated.response());
            PairwiseEvalResult pair = null; if (pairwise != null && pairwiseResponses != null && pairwiseResponses.containsKey(c.caseId())) { PairwiseResponse p = pairwiseResponses.get(c.caseId()); pair = pairwise.compare(c, new PairwiseResponse(c.caseId(), c.schemaVersion(), generated.response()), p); }
            reports.add(new EvalRunReport.CaseReport(c.caseId(), generated.response(), result, pair, generated.generatorMetadata())); }
        return new EvalRunReport("1", config, Instant.now(), reports, aggregate(cases, reports));
    }
    private EvalRunReport.Aggregate aggregate(List<EvalCase> cases, List<EvalRunReport.CaseReport> reports) {
        long pass=0, fail=0, uneval=0; Map<String, long[]> byCat = new HashMap<>(); Map<String, List<Integer>> quality = new HashMap<>(); Map<String,Long> pw = new HashMap<>(Map.of("A",0L,"B",0L,"TIE",0L));
        for (int i=0;i<cases.size();i++) { EvalCase c=cases.get(i); EvalResult r=reports.get(i).absolute(); for (int x=0;x<r.hardResults().size();x++) { HardConstraintResult h=r.hardResults().get(x); if(h.status()==HardStatus.PASS) pass++; else if(h.status()==HardStatus.FAIL) fail++; else uneval++; String cat=c.hardExpectations().get(x).category(); long[] a=byCat.computeIfAbsent(cat,k->new long[3]); a[h.status().ordinal()]++; } for(QualityScore q:r.qualityScores()) quality.computeIfAbsent(q.dimension(),k->new ArrayList<>()).add(q.score()); PairwiseEvalResult p=reports.get(i).pairwise(); if(p!=null && p.winner()!=null) pw.compute(p.winner().name(),(k,v)->v+1); }
        Map<String,EvalRunReport.HardRate> rates=new HashMap<>(); byCat.forEach((k,v)->rates.put(k,new EvalRunReport.HardRate(v[0],v[1],v[2]))); Map<String,Double> avgs=new HashMap<>(); quality.forEach((k,v)->avgs.put(k,v.stream().mapToInt(Integer::intValue).average().orElse(0))); return new EvalRunReport.Aggregate(cases.size(),pass,fail,uneval,rates,avgs,pw);
    }
}
