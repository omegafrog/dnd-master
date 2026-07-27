package com.dndmaster.diceroll.infrastructure.http;

public final class AdjudicationDeliveryFailedException extends RuntimeException {
    public AdjudicationDeliveryFailedException(Throwable cause) { super("AI adjudication delivery failed", cause); }
}
