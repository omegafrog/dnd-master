package com.dndmaster.combatmap.domain;
import java.util.Objects; import java.util.UUID;
public record PlayerId(UUID value) { public PlayerId { Objects.requireNonNull(value); } }
