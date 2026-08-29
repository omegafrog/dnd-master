package com.dndmaster.adventure.application.combat;

/**
 * A combat action was rejected because the runtime state makes it invalid.
 *
 * <p>This is deliberately separate from {@link CrossContextCallException}:
 * the latter represents an integration failure, while this exception is a
 * client-correctable combat decision.</p>
 */
public final class RuntimeCombatRejectionException extends RuntimeException {
    public static final String ERROR_CODE = "COMBAT_CHARACTER_UNUSABLE";
    public static final String ZERO_HIT_POINTS_MESSAGE =
            "character cannot perform combat action with zero hit points";

    public RuntimeCombatRejectionException(String message) {
        super(message);
    }
}
