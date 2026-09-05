package com.dndmaster.adventure.combat;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.adventure.domain.combat.CombatEndProposal;
import com.dndmaster.adventure.domain.combat.CombatEndProposalPolicy;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CombatEndProposalPolicyTest {
    @Test
    void accepts_only_a_valid_gm_proposal_for_the_active_encounter() {
        UUID adventureId = UUID.randomUUID();
        UUID encounterId = UUID.randomUUID();
        var proposal = CombatEndProposal.gm(adventureId, encounterId,
                CombatEndProposal.Reason.ENEMIES_DEFEATED, "적이 모두 쓰러졌습니다.");

        assertTrue(CombatEndProposalPolicy.validate(proposal, adventureId, encounterId).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> CombatEndProposalPolicy.requireValid(
                new CombatEndProposal(adventureId, encounterId, CombatEndProposal.Source.PLAYER,
                        CombatEndProposal.Reason.SURRENDER, "플레이어가 종료", true), adventureId, encounterId));
        assertThrows(IllegalArgumentException.class, () -> CombatEndProposalPolicy.requireValid(
                CombatEndProposal.gm(UUID.randomUUID(), encounterId,
                        CombatEndProposal.Reason.SURRENDER, "다른 모험"), adventureId, encounterId));
    }
}
