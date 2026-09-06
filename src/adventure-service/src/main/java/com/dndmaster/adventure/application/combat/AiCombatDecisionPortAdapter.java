package com.dndmaster.adventure.application.combat;

import com.dndmaster.adventure.domain.combat.FreeFormActionPlan;
import java.util.Objects;
import java.util.function.Function;

/** Converts an AI provider function into the application-owned typed decision port. */
public final class AiCombatDecisionPortAdapter implements AiCombatDecisionPort {
    private final Function<FreeFormCombatContext, FreeFormActionPlan> provider;

    public AiCombatDecisionPortAdapter(Function<FreeFormCombatContext, FreeFormActionPlan> provider) {
        this.provider = Objects.requireNonNull(provider, "AI free-form provider must not be null");
    }

    @Override
    public FreeFormActionPlan interpretFreeForm(FreeFormCombatContext context) {
        return Objects.requireNonNull(provider.apply(Objects.requireNonNull(context)),
                "AI free-form proposal must not be null");
    }
}
