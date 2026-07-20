package com.dndmaster.diceroll.infrastructure.http;

import com.dndmaster.diceroll.domain.RollId;

public interface AdjudicationDeliveryStateStore {
    DeliveryAttempt begin(String deliveryKey, RollId rollId, String payloadHash);
    void markFailed(String deliveryKey, long expectedVersion, String reason);
    void markCompleted(String deliveryKey, long expectedVersion);
}
