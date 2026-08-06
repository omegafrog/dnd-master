package com.dndmaster.adventure.application.runtime;

public enum StoryEvidenceVisibility {
    PLAYER_VISIBLE, GM_ONLY, NPC_PRIVATE, REVEALED_AFTER_EVENT, DISCOVERED, PUBLIC_SUMMARY;

    public boolean visibleToPlayer(String disclosureEvent, long disclosureTurn, java.util.Set<String> events, long turn) {
        return switch (this) {
            case PLAYER_VISIBLE, PUBLIC_SUMMARY -> true;
            case DISCOVERED -> disclosureEvent != null && events.contains(disclosureEvent) && turn >= disclosureTurn;
            case REVEALED_AFTER_EVENT -> disclosureEvent != null && events.contains(disclosureEvent)
                    && turn >= disclosureTurn;
            case GM_ONLY, NPC_PRIVATE -> false;
        };
    }
}
