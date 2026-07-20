package com.dndmaster.combatmap.infrastructure.persistence;
public final class OptimisticCombatMapLockException extends RuntimeException{public OptimisticCombatMapLockException(){super("combat map concurrently modified");}}
