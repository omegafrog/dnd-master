package com.dndmaster.adventure.evidence;
import java.util.Set;
public final class RuleGuidanceEvidenceSufficiencyPolicy implements EvidenceSufficiencyPolicy {
 public String policyId(){return "RULE_GUIDANCE";} public Set<String> documentTypes(){return Set.of("RULEBOOK");} public FinalInsufficiency finalInsufficiency(){return FinalInsufficiency.EVIDENCE_UNAVAILABLE;}
}
