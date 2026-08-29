package com.dndmaster.adventure.application.runtime;

import java.util.List;

public interface ExemplarRetrieverPort {
    List<ExemplarResult> retrieve(ExemplarQuery query);
}
