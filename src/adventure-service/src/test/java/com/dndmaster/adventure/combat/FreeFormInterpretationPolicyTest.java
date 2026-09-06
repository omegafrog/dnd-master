package com.dndmaster.adventure.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.adventure.application.combat.AiCombatDecisionPort;
import com.dndmaster.adventure.application.combat.AiCombatDecisionPortAdapter;
import com.dndmaster.adventure.application.combat.FreeFormCombatContext;
import com.dndmaster.adventure.domain.combat.FreeFormActionPlan;
import com.dndmaster.adventure.domain.combat.FreeFormActionDeclaration;
import com.dndmaster.adventure.domain.combat.FreeFormInterpretationPolicy;
import com.dndmaster.adventure.domain.combat.TurnResourceCost;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FreeFormInterpretationPolicyTest {
    @Test
    void accepts_a_declaration_even_when_it_has_no_structured_action_mapping() {
        UUID actorId = UUID.randomUUID();

        FreeFormActionDeclaration declaration = FreeFormInterpretationPolicy.accept(actorId,
                "throw ham at the goblin");

        assertEquals(actorId, declaration.actorId());
        assertEquals("throw ham at the goblin", declaration.text());
    }

    @Test
    void rejects_only_malformed_free_form_input_at_the_input_boundary() {
        assertThrows(IllegalArgumentException.class,
                () -> FreeFormInterpretationPolicy.accept(UUID.randomUUID(), " " + String.valueOf((char) 0) + " "));
    }

    @Test
    void adapter_exposes_a_typed_ai_proposal_contract() {
        UUID actorId = UUID.randomUUID();
        FreeFormActionPlan plan = FreeFormActionPlan.narrativeOnly(actorId,
                TurnResourceCost.actionOnly(), "The goblin hesitates.", "The goblin hesitates.");
        AiCombatDecisionPort port = new AiCombatDecisionPortAdapter(context -> plan);

        assertEquals(plan, port.interpretFreeForm(new FreeFormCombatContext(
                null, new FreeFormActionDeclaration(actorId, "throw ham at the goblin"))));
    }
}
