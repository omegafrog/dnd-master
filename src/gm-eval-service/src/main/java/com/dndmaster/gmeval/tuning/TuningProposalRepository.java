package com.dndmaster.gmeval.tuning;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class TuningProposalRepository {
    private final TuningProposalStore store;
    private final Map<String, TuningProposalRecord> records = new LinkedHashMap<>();

    public TuningProposalRepository(TuningProposalStore store) {
        this.store = Objects.requireNonNull(store, "tuning proposal store required");
        for (TuningProposalRecord record : store.load()) {
            if (records.put(record.proposal().proposalId(), record) != null) throw new IllegalArgumentException("duplicate tuning proposal");
        }
    }

    public synchronized void save(TuningProposalRecord record) {
        Objects.requireNonNull(record, "tuning proposal record required");
        TuningProposalRecord previous = records.get(record.proposal().proposalId());
        if (previous != null && !previous.equals(record)) throw new IllegalArgumentException("tuning proposal is immutable: " + record.proposal().proposalId());
        records.put(record.proposal().proposalId(), record);
        store.save(List.copyOf(records.values()));
    }

    public synchronized Optional<TuningProposalRecord> find(String proposalId) {
        return Optional.ofNullable(records.get(proposalId));
    }

    public synchronized List<TuningProposalView> listProjections() {
        return records.values().stream().map(TuningProposalView::from).toList();
    }

    public synchronized TuningProposalView readProjection(String proposalId) {
        return find(proposalId).map(TuningProposalView::from)
                .orElseThrow(() -> new IllegalArgumentException("tuning proposal not found: " + proposalId));
    }

    public synchronized List<TuningAuditEntry> audit(String proposalId) {
        return find(proposalId).map(TuningProposalRecord::audit)
                .orElseThrow(() -> new IllegalArgumentException("tuning proposal not found: " + proposalId));
    }

    public synchronized java.util.Set<TuningFailureCategory> failureTaxonomy(String proposalId) {
        return readProjection(proposalId).failureTaxonomy();
    }
}
