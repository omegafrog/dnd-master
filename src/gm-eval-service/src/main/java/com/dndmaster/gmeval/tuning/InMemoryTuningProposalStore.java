package com.dndmaster.gmeval.tuning;

import java.util.ArrayList;
import java.util.List;

public final class InMemoryTuningProposalStore implements TuningProposalStore {
    private List<TuningProposalRecord> records = List.of();

    @Override public List<TuningProposalRecord> load() { return List.copyOf(records); }
    @Override public void save(List<TuningProposalRecord> next) { records = List.copyOf(new ArrayList<>(next)); }
}
