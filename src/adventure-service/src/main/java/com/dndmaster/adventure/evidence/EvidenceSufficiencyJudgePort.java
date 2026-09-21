package com.dndmaster.adventure.evidence;
@FunctionalInterface public interface EvidenceSufficiencyJudgePort { SufficiencyDecision judge(EvidenceSufficiencyRequest request); }
