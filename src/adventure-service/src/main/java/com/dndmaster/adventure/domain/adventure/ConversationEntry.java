package com.dndmaster.adventure.domain.adventure;

public record ConversationEntry(long sequence, String speaker, String content) {
    public ConversationEntry {
        if (sequence < 0) throw new IllegalArgumentException("sequence must not be negative");
        speaker = required(speaker, "speaker");
        content = required(content, "content");
        if (content.length() > 8192) throw new IllegalArgumentException("content is too long");
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}
