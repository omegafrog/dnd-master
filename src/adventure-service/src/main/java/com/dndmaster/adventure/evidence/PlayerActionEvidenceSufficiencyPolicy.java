package com.dndmaster.adventure.evidence;
import java.util.Set;
public final class PlayerActionEvidenceSufficiencyPolicy implements EvidenceSufficiencyPolicy {
 public String policyId(){return "PLAYER_ACTION";} public Set<String> documentTypes(){return Set.of("RULEBOOK","STORYBOOK");} public FinalInsufficiency finalInsufficiency(){return FinalInsufficiency.LIMITED_PLAYER_ACTION;}
}
