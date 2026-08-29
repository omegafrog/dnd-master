package com.dndmaster.gmeval.domain;
import java.util.*;
public final class AbsoluteEvaluationService {
 public EvalResult evaluate(EvalCase c, String response) { if (response == null) throw new IllegalArgumentException("response required");
  List<HardConstraintResult> out=new ArrayList<>(); String lower=response.toLowerCase(Locale.ROOT);
  for (HardExpectation e:c.hardExpectations()) { if (e instanceof HardExpectation.ForbiddenFact f) out.add(lower.contains(f.fact().toLowerCase(Locale.ROOT)) ? HardConstraintResult.fail(e.id(),"forbidden fact disclosed",f.fact()) : HardConstraintResult.pass(e.id(),"fact absent"));
   else if (e instanceof HardExpectation.RequiredFact f) out.add(lower.contains(f.fact().toLowerCase(Locale.ROOT)) ? HardConstraintResult.pass(e.id(),f.fact()) : HardConstraintResult.fail(e.id(),"required fact omitted",f.fact()));
   else if (e instanceof HardExpectation.RuleContradiction f) out.add(lower.contains(f.authoritativeRule().toLowerCase(Locale.ROOT)) ? HardConstraintResult.pass(e.id(),f.authoritativeRule()) : HardConstraintResult.unevaluated(e.id(),"semantic rule contradiction requires structured resolution"));
   else if (e instanceof HardExpectation.StateMutation f) out.add(HardConstraintResult.unevaluated(e.id(),"state mutation requires structured response facts"));
   else if (e instanceof HardExpectation.AgencyViolation f) out.add(lower.contains(f.voluntaryAction().toLowerCase(Locale.ROOT)) ? HardConstraintResult.fail(e.id(),"player agency invented",f.voluntaryAction()) : HardConstraintResult.pass(e.id(),"voluntary action absent"));
   else out.add(HardConstraintResult.unevaluated(e.id(),((HardExpectation.Unsupported)e).reason())); }
  return new EvalResult(out,List.of()); }
}
