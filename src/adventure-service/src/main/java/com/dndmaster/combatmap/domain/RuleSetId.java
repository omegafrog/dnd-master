package com.dndmaster.combatmap.domain;
import java.util.Objects; import java.util.UUID;
public record RuleSetId(UUID value) { public RuleSetId { Objects.requireNonNull(value); } }
