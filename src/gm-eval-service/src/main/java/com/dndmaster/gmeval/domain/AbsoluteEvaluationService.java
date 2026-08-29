package com.dndmaster.gmeval.domain;
import java.util.*;
public final class AbsoluteEvaluationService {
 private final RubricJudgePort rubricJudge;
 public AbsoluteEvaluationService() { this(null); }
 public AbsoluteEvaluationService(RubricJudgePort rubricJudge) { this.rubricJudge = rubricJudge; }
 public EvalResult evaluate(EvalCase c, String response) { if (response == null) throw new IllegalArgumentException("response required");
  List<HardConstraintResult> out=new ArrayList<>(); String lower=response.toLowerCase(Locale.ROOT);
  for (HardExpectation e:c.hardExpectations()) { if (e instanceof HardExpectation.ForbiddenFact f) out.add(asserted(lower,f.fact()) ? HardConstraintResult.fail(e.id(),"forbidden fact disclosed",f.fact()) : HardConstraintResult.pass(e.id(),"fact absent"));
   else if (e instanceof HardExpectation.RequiredFact f) out.add(asserted(lower,f.fact()) ? HardConstraintResult.pass(e.id(),f.fact()) : HardConstraintResult.fail(e.id(),"required fact omitted or contradicted", ""));
   else if (e instanceof HardExpectation.RuleContradiction f) out.add(explicitContradiction(lower,f.authoritativeRule()) ? HardConstraintResult.fail(e.id(),"response contradicts resolved rule",excerpt(lower, contradictionPhrase(lower,f.authoritativeRule()))) : asserted(lower,f.authoritativeRule()) ? HardConstraintResult.pass(e.id(),f.authoritativeRule()) : HardConstraintResult.unevaluated(e.id(),"rule wording is not structurally comparable"));
   else if (e instanceof HardExpectation.StateMutation f) out.add(stateResult(lower,e.id(),f));
   else if (e instanceof HardExpectation.AgencyViolation f) out.add(asserted(lower,f.voluntaryAction()) ? HardConstraintResult.fail(e.id(),"player agency invented",f.voluntaryAction()) : HardConstraintResult.pass(e.id(),"voluntary action absent"));
   else out.add(HardConstraintResult.unevaluated(e.id(),((HardExpectation.Unsupported)e).reason())); }
  if (c.rubrics().isEmpty()) return new EvalResult(out, List.of());
  if (rubricJudge == null) return new EvalResult(out, List.of(), "rubric judge not configured");
  try {
   RubricJudgeResponse judged = rubricJudge.judge(new RubricJudgeRequest(c, response, c.rubrics()));
   return new EvalResult(out, RubricJudgeResponseValidator.validate(c.rubrics(), judged), null);
  } catch (RuntimeException failure) {
   return new EvalResult(out, List.of(), failure.getMessage() == null ? "invalid rubric judge response" : failure.getMessage());
  }
 }
 private static boolean asserted(String response,String fact) { String n=fact.toLowerCase(Locale.ROOT); int i=0; while((i=response.indexOf(n,i))>=0) { String p=response.substring(Math.max(0,i-5),i); if(!p.matches(".*\\b(not|never|no)\\s+$")) return true; i+=n.length(); } return false; }
 private static HardConstraintResult stateResult(String response,String id,HardExpectation.StateMutation f) { String fact=f.stateFact().toLowerCase(Locale.ROOT), expected=f.expectedValue().toLowerCase(Locale.ROOT); if(!response.contains(fact)) return HardConstraintResult.unevaluated(id,"state fact not explicitly asserted in response"); List<String> claims=new ArrayList<>(); for(String sentence:response.split("[.!?]")) if(sentence.contains(fact)||sentence.contains("it is ")) claims.add(sentence.trim()); if(claims.isEmpty()) return HardConstraintResult.unevaluated(id,"state fact not explicitly asserted in response"); String joined=String.join("; ",claims); String opposite=expected.equals("closed")?"open":expected.equals("open")?"closed":""; boolean expectedSeen=response.contains(expected), conflict=!opposite.isEmpty() && response.contains(opposite); if(conflict) return HardConstraintResult.fail(id,"response mutates authoritative state",excerpt(response,joined)); return expectedSeen ? HardConstraintResult.pass(id,excerpt(response,joined)) : HardConstraintResult.unevaluated(id,"state value is not structurally comparable"); }
 private static boolean explicitContradiction(String response,String rule) { String r=rule.toLowerCase(Locale.ROOT); if(response.contains("not "+r)||response.contains("never "+r)) return true; if(r.contains(" is ")) { String[] p=r.split(" is ",2); String o=p[1].equals("closed")?"open":p[1].equals("open")?"closed":p[1].equals("alive")?"dead":p[1].equals("dead")?"alive":""; return (!o.isEmpty() && (response.contains(p[0]+" is "+o)||response.contains(p[0]+" is not "+p[1]))); } return false; }
 private static String contradictionPhrase(String response,String rule) { int i=response.indexOf("not "+rule); if(i<0 && rule.contains(" is ")) { String[] p=rule.split(" is ",2); String o=p[1].equals("closed")?"open":p[1].equals("open")?"closed":""; i=response.indexOf(p[0]+" is not "+p[1]); if(i<0 && !o.isEmpty()) i=response.indexOf(p[0]+" is "+o); } return i<0?rule:response.substring(Math.max(0,i-30),Math.min(response.length(),i+rule.length()+30)); }
 private static String excerpt(String response,String value) { return value == null || value.isBlank() ? "" : value.length() > 160 ? value.substring(0,160) : value; }
}
