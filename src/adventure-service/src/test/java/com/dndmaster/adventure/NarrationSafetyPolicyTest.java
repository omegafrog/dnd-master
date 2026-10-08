package com.dndmaster.adventure;

import static org.assertj.core.api.Assertions.assertThat;

import com.dndmaster.adventure.application.runtime.DefaultNarrationSafetyPolicy;
import com.dndmaster.adventure.application.runtime.EvidencePack;
import com.dndmaster.adventure.application.runtime.NarrationSafetyRequest;
import com.dndmaster.adventure.domain.adventure.AdventureContext;
import java.util.List;
import org.junit.jupiter.api.Test;

class NarrationSafetyPolicyTest {
    private final DefaultNarrationSafetyPolicy policy = new DefaultNarrationSafetyPolicy();

    @Test
    void allows_quoted_npc_dialogue_when_it_does_not_reveal_hidden_facts() {
        assertThat(policy.assess(new NarrationSafetyRequest(
                "Glowkindle은 고개를 젓습니다. “쥐약은 맥주를 망칠 수 있다네.”",
                new EvidencePack(List.of(), List.of(), List.of()),
                new AdventureContext("brewery", null, null, null), "propose rat poison", List.of())).approved())
                .isTrue();
        assertThat(policy.assess(new NarrationSafetyRequest(
                "Glowkindle said, \"The poison might spoil the beer.\"",
                new EvidencePack(List.of(), List.of(), List.of()),
                new AdventureContext("brewery", null, null, null), "propose rat poison", List.of())).approved())
                .isTrue();
    }

    @Test
    void rejects_an_individual_unrevealed_fact_but_allows_a_revealed_fact() {
        NarrationSafetyRequest request = new NarrationSafetyRequest(
                "The caretaker is secretly the missing heir.", new EvidencePack(List.of(), List.of(), List.of()),
                new AdventureContext("hall", null, null, null), "attack",
                List.of("The caretaker is secretly the missing heir."));

        assertThat(policy.assess(request).approved()).isFalse();
        assertThat(policy.assess(new NarrationSafetyRequest(
                "The caretaker is secretly the missing heir.", request.evidencePack(), request.currentContext(),
                request.action(), List.of())).approved()).isTrue();
    }

    @Test
    void privacy_rejection_reason_does_not_repeat_private_fact_text() {
        var assessment = policy.assess(new NarrationSafetyRequest(
                "The caretaker is secretly the missing heir.", new EvidencePack(List.of(), List.of(), List.of()),
                new AdventureContext("hall", null, null, null), "attack",
                List.of("The caretaker is secretly the missing heir.")));

        assertThat(assessment.reason()).doesNotContain("missing heir");
    }
}
