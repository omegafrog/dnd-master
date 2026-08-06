package com.dndmaster.aigamemaster.retrieval;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record RetrievalMetrics(double recallAt1,double recallAt5,double precisionAt5,double mrr,double ndcgAt5,double secretRetrievalRate,double scopeViolationRate,double latencyP50Ms,double latencyP95Ms,int cases){
 public static RetrievalMetrics evaluate(List<RetrievalEvaluationCase> cases,List<RetrievalEvaluationResult> results){
  if(cases==null||results==null||cases.isEmpty()||cases.size()!=results.size())throw new IllegalArgumentException("case/result count mismatch");
  Map<String,RetrievalEvaluationCase> byId=cases.stream().collect(Collectors.toMap(RetrievalEvaluationCase::id,c->c)); if(results.stream().map(RetrievalEvaluationResult::caseId).distinct().count()!=cases.size()||results.stream().anyMatch(r->!byId.containsKey(r.caseId())))throw new IllegalArgumentException("results must contain each corpus case exactly once"); double r1=0,r5=0,p5=0,mrr=0,ndcg=0,secrets=0,scope=0; double[] lat=new double[results.size()];
  for(int i=0;i<results.size();i++){var result=results.get(i);var c=byId.get(result.caseId());if(c==null)throw new IllegalArgumentException("unknown case: "+result.caseId());var top=result.candidates().stream().limit(5).toList();int relevant=0,first=0;boolean secret=false,violation=false;
   var seen=new java.util.HashSet<RetrievalReference>(); for(int j=0;j<top.size();j++){var candidate=top.get(j);if(!seen.add(candidate.reference()))continue;var relevance=!candidate.scopeMatches(c)?Relevance.IRRELEVANT:c.relevance(candidate.reference());if(relevance==Relevance.RELEVANT){relevant++;if(first==0)first=j+1;}if(c.relevance(candidate.reference())==Relevance.FORBIDDEN)secret=true;if(!candidate.scopeMatches(c))violation=true;}
   r1+=first==1?1:0;r5+=first>0?1:0;p5+=seen.isEmpty()?0:relevant/(double)Math.min(5,seen.size());mrr+=first==0?0:1.0/first;double dcg=0;var seenForDcg=new java.util.HashSet<RetrievalReference>();for(int j=0;j<top.size();j++)if(seenForDcg.add(top.get(j).reference())&&top.get(j).scopeMatches(c)&&c.relevance(top.get(j).reference())==Relevance.RELEVANT)dcg+=1/(Math.log(j+2)/Math.log(2));int ideal=Math.min(5,c.expected().size()+c.alternatives().size());double idcg=0;for(int j=0;j<ideal;j++)idcg+=1/(Math.log(j+2)/Math.log(2));ndcg+=idcg==0?0:dcg/idcg;secrets+=secret?1:0;scope+=violation?1:0;lat[i]=result.latencyMs();
  } java.util.Arrays.sort(lat);return new RetrievalMetrics(r1/cases.size(),r5/cases.size(),p5/cases.size(),mrr/cases.size(),ndcg/cases.size(),secrets/cases.size(),scope/cases.size(),percentile(lat,.5),percentile(lat,.95),cases.size());
 }
 private static double percentile(double[] a,double p){if(a.length==1)return a[0];double x=p*(a.length-1);int lo=(int)Math.floor(x),hi=(int)Math.ceil(x);return a[lo]+(a[hi]-a[lo])*(x-lo);}
}
