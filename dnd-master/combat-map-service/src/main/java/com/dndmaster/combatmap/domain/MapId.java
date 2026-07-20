package com.dndmaster.combatmap.domain;
import java.util.Objects; import java.util.UUID;
public record MapId(UUID value) { public MapId { Objects.requireNonNull(value); } }
