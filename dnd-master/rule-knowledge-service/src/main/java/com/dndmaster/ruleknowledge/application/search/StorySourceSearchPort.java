package com.dndmaster.ruleknowledge.application.search;

import java.util.List;

public interface StorySourceSearchPort {
    List<StorySourceEvidence> search(StorySourceSearchQuery query, float[] queryEmbedding, boolean activeContextOnly);
}
