package com.dndmaster.diceroll.infrastructure.http;

import com.dndmaster.diceroll.domain.DiceRoll;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

public final class DiceRollHttpDeliveryAdapter {
    private final AdventureRollConditionClient adventureClient;
    private final AiJudgmentClient aiClient;
    private final AdjudicationDeliveryStateStore stateStore;

    public DiceRollHttpDeliveryAdapter(
            AdventureRollConditionClient adventureClient,
            AiJudgmentClient aiClient,
            AdjudicationDeliveryStateStore stateStore) {
        this.adventureClient = Objects.requireNonNull(adventureClient);
        this.aiClient = Objects.requireNonNull(aiClient);
        this.stateStore = Objects.requireNonNull(stateStore);
    }

    public DeliveryStatus deliverForAdjudication(String deliveryKey, DiceRoll roll) {
        if (deliveryKey == null || deliveryKey.isBlank()) throw new IllegalArgumentException("delivery key must not be blank");
        Objects.requireNonNull(roll, "roll must not be null").result().orElseThrow();
        adventureClient.requireAllowed(roll.adventureId(), roll.ruleSetId(), roll.scope());
        DeliveryAttempt attempt = stateStore.begin(deliveryKey.trim(), roll.id(), payloadHash(roll));
        if (!attempt.shouldDeliver()) return attempt.status();
        try {
            aiClient.deliver(deliveryKey.trim(), roll);
        } catch (RuntimeException exception) {
            stateStore.markFailed(deliveryKey.trim(), attempt.version(), "AI judgment HTTP call failed");
            throw new AdjudicationDeliveryFailedException(exception);
        }
        stateStore.markCompleted(deliveryKey.trim(), attempt.version());
        return DeliveryStatus.COMPLETED;
    }

    private static String payloadHash(DiceRoll roll) {
        String payload = roll.id().value() + "|" + roll.adventureId().value() + "|" + roll.ruleSetId().value()
                + "|" + roll.scope() + "|" + roll.expression() + "|" + roll.result().orElseThrow();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 unavailable", exception); }
    }
}
