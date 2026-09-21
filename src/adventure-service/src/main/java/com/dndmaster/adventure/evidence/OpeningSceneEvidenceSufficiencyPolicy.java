package com.dndmaster.adventure.evidence;
import java.util.Set;
public final class OpeningSceneEvidenceSufficiencyPolicy implements EvidenceSufficiencyPolicy {
 public String policyId(){return "OPENING_SCENE";} public Set<String> documentTypes(){return Set.of("STORYBOOK");} public FinalInsufficiency finalInsufficiency(){return FinalInsufficiency.OPENING_PREPARATION_FAILED;}
}
