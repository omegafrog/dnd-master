package com.dndmaster.diceroll;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.diceroll.domain.*;
import com.dndmaster.diceroll.infrastructure.http.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DiceRollHttpFailureTest {
    @Test
    void failedAiHttpCallNeverCompletesAndRetryUsesSameIdempotencyKeyOnce() {
        FakeDeliveryStore states = new FakeDeliveryStore();
        RecordingAdventureClient adventure = new RecordingAdventureClient();
        FailingOnceAiClient ai = new FailingOnceAiClient();
        DiceRollHttpDeliveryAdapter adapter = new DiceRollHttpDeliveryAdapter(adventure, ai, states);
        DiceRoll roll = completedRoll();

        assertThrows(
                AdjudicationDeliveryFailedException.class,
                () -> adapter.deliverForAdjudication("delivery-key", roll));
        assertEquals(DeliveryStatus.FAILED, states.status("delivery-key"));

        assertEquals(DeliveryStatus.COMPLETED, adapter.deliverForAdjudication("delivery-key", roll));
        assertEquals(DeliveryStatus.COMPLETED, states.status("delivery-key"));
        assertEquals(DeliveryStatus.COMPLETED, adapter.deliverForAdjudication("delivery-key", roll));

        assertEquals(List.of("delivery-key", "delivery-key"), ai.keys);
        assertEquals(3, adventure.calls);
    }

    @Test
    void adventureConditionFailureStopsBeforeDeliveryStateOrAiCall() {
        FakeDeliveryStore states = new FakeDeliveryStore();
        FailingOnceAiClient ai = new FailingOnceAiClient();
        DiceRollHttpDeliveryAdapter adapter = new DiceRollHttpDeliveryAdapter(
                (adventureId, ruleSetId, scope) -> { throw new IllegalStateException("adventure rejected roll"); },
                ai,
                states);

        assertThrows(IllegalStateException.class, () -> adapter.deliverForAdjudication("blocked", completedRoll()));
        assertEquals(null, states.status("blocked"));
        assertEquals(List.of(), ai.keys);
    }

    private static DiceRoll completedRoll() {
        DiceExpression expression = new DiceExpression(1, 20, 0);
        DiceRoll roll = new DiceRoll(
                RollId.generate(), new AdventureId(UUID.randomUUID()), new RuleSetId(UUID.randomUUID()), RollScope.NPC, expression);
        roll.recordBuiltInResult(DiceResult.forExpression(expression, List.of(12)));
        return roll;
    }

    private static final class RecordingAdventureClient implements AdventureRollConditionClient {
        private int calls;
        @Override public void requireAllowed(AdventureId adventureId, RuleSetId ruleSetId, RollScope scope) { calls++; }
    }

    private static final class FailingOnceAiClient implements AiJudgmentClient {
        private final java.util.ArrayList<String> keys = new java.util.ArrayList<>();
        private boolean fail = true;
        @Override public void deliver(String idempotencyKey, DiceRoll roll) {
            keys.add(idempotencyKey);
            if (fail) { fail = false; throw new IllegalStateException("timeout"); }
        }
    }

    private static final class FakeDeliveryStore implements AdjudicationDeliveryStateStore {
        private final Map<String, Entry> entries = new HashMap<>();
        @Override public DeliveryAttempt begin(String key, RollId rollId, String hash) {
            Entry entry = entries.get(key);
            if (entry == null) {
                entries.put(key, new Entry(DeliveryStatus.PENDING, 0));
                return new DeliveryAttempt(DeliveryStatus.PENDING, 0, true);
            }
            if (entry.status == DeliveryStatus.COMPLETED) return new DeliveryAttempt(entry.status, entry.version, false);
            if (entry.status == DeliveryStatus.FAILED) {
                entry.status = DeliveryStatus.PENDING; entry.version++;
                return new DeliveryAttempt(entry.status, entry.version, true);
            }
            return new DeliveryAttempt(entry.status, entry.version, false);
        }
        @Override public void markFailed(String key, long version, String reason) { transition(key, version, DeliveryStatus.FAILED); }
        @Override public void markCompleted(String key, long version) { transition(key, version, DeliveryStatus.COMPLETED); }
        private void transition(String key, long version, DeliveryStatus status) {
            Entry entry = entries.get(key);
            if (entry.version != version || entry.status != DeliveryStatus.PENDING) throw new IllegalStateException("stale");
            entry.status = status; entry.version++;
        }
        private DeliveryStatus status(String key) { Entry entry = entries.get(key); return entry == null ? null : entry.status; }
        private static final class Entry {
            private DeliveryStatus status; private long version;
            private Entry(DeliveryStatus status, long version) { this.status = status; this.version = version; }
        }
    }
}
