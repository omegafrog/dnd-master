package com.dndmaster.gmeval.tuning;

import java.util.List;

public interface TuningProposalStore {
    List<TuningProposalRecord> load();
    void save(List<TuningProposalRecord> records);
}
