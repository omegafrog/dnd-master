package com.dndmaster.adventure.application.runtime;

import java.util.List;

/** Separate catalog/index boundary; implementations must not share the Knowledge index. */
public interface ExemplarCatalogIndexPort {
    List<ExemplarResult> retrieve(ExemplarQuery query);
}
