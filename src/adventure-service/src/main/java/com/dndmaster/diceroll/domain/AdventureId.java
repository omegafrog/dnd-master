package com.dndmaster.diceroll.domain;

import java.util.Objects;
import java.util.UUID;

public record AdventureId(UUID value) {
    public AdventureId { Objects.requireNonNull(value, "adventure id must not be null"); }
}
