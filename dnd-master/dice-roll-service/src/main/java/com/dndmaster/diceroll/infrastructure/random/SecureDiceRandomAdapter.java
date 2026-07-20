package com.dndmaster.diceroll.infrastructure.random;

import com.dndmaster.diceroll.application.DiceRandomPort;
import java.security.SecureRandom;
import java.util.Objects;

public final class SecureDiceRandomAdapter implements DiceRandomPort {
    private final SecureRandom random;

    public SecureDiceRandomAdapter() { this(new SecureRandom()); }

    SecureDiceRandomAdapter(SecureRandom random) {
        this.random = Objects.requireNonNull(random, "secure random must not be null");
    }

    @Override public int nextInt(int bound) {
        if (bound <= 0) throw new IllegalArgumentException("bound must be positive");
        return random.nextInt(bound);
    }
}
