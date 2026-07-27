package com.dndmaster.ruleknowledge.application.registration;

import com.dndmaster.ruleknowledge.domain.rulebook.Rulebook;
import java.util.Objects;

public record RegisteredRulebook(Rulebook rulebook, StoredRulebookFile storedFile) {
    public RegisteredRulebook {
        Objects.requireNonNull(rulebook, "rulebook must not be null");
        Objects.requireNonNull(storedFile, "storedFile must not be null");
    }
}
