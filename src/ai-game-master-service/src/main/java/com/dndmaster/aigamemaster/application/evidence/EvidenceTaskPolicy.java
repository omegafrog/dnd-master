package com.dndmaster.aigamemaster.application.evidence;

/** The four server-owned situations that may request an evidence sufficiency decision. */
public enum EvidenceTaskPolicy {
    PLAYER_ACTION,
    SCENARIO_PREPARATION,
    OPENING_SCENE,
    RULE_GUIDANCE;

    String fixedInstruction() {
        return "POLICY=" + name() + "\n"
                + "Use only supplied evidence candidates. Do not invent rulebook or scenario facts. "
                + "Return exactly one JSON object that follows the requested output contract.";
    }
}
