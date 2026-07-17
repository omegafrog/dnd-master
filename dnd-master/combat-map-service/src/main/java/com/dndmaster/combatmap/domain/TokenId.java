package com.dndmaster.combatmap.domain;
import java.util.Objects; import java.util.UUID;
public record TokenId(UUID value) { public TokenId { Objects.requireNonNull(value); } }
