package com.dndmaster.adventure.domain.runtime;

import java.util.Objects;
import java.util.UUID;

/** One authenticated player intent entering the GM lifecycle. */
public sealed interface GmInput permits GmInput.TextInput, GmInput.MapActionInput, GmInput.MetaQuestionInput {
    String type();
    String actionText();

    record TextInput(String text) implements GmInput {
        public TextInput { text = required(text, "text"); }
        @Override public String type() { return "TEXT"; }
        @Override public String actionText() { return text; }
    }

    record MapActionInput(UUID mapId, long mapVersion, String action) implements GmInput {
        public MapActionInput {
            if (mapId == null) throw new IllegalArgumentException("map id must not be null");
            if (mapVersion < 0) throw new IllegalArgumentException("map version must not be negative");
            action = required(action, "action");
        }
        @Override public String type() { return "MAP_ACTION"; }
        @Override public String actionText() { return action; }
    }

    record MetaQuestionInput(String question) implements GmInput {
        public MetaQuestionInput { question = required(question, "question"); }
        @Override public String type() { return "META_QUESTION"; }
        @Override public String actionText() { return question; }
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}
