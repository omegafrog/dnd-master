package com.dndmaster.adventure.evidence;
import java.util.Set;
public final class ScenarioPreparationEvidenceSufficiencyPolicy implements EvidenceSufficiencyPolicy {
 public String policyId(){return "SCENARIO_PREPARATION";} public Set<String> documentTypes(){return Set.of("STORYBOOK");} public FinalInsufficiency finalInsufficiency(){return FinalInsufficiency.PARTIAL_OR_INVALID_SCENARIO;}
}
