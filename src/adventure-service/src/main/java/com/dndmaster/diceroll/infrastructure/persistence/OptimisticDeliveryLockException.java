package com.dndmaster.diceroll.infrastructure.persistence;

public final class OptimisticDeliveryLockException extends RuntimeException {
    public OptimisticDeliveryLockException() { super("adjudication delivery was concurrently modified"); }
}
