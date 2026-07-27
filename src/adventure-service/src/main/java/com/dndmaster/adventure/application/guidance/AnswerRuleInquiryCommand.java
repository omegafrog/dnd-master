package com.dndmaster.adventure.application.guidance;

import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.adventure.RuleSetId;
import com.dndmaster.adventure.domain.inquiry.InquiryId;
import java.util.Objects;

public record AnswerRuleInquiryCommand(
        InquiryId inquiryId, AdventureId adventureId, RuleSetId ruleSetId,
        OwnerPlayerId requestingOwner, String situation) {
    public AnswerRuleInquiryCommand {
        Objects.requireNonNull(inquiryId, "inquiry id must not be null");
        Objects.requireNonNull(adventureId, "adventure id must not be null");
        Objects.requireNonNull(ruleSetId, "rule set id must not be null");
        Objects.requireNonNull(requestingOwner, "requesting owner must not be null");
        if (situation == null || situation.isBlank()) throw new IllegalArgumentException("situation must not be blank");
        situation = situation.trim();
    }
}
