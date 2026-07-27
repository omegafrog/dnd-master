package com.dndmaster.adventure.application.scenario.preparation;

import java.util.List;

public record RuntimeOptionsView(
        String defaultEngineId,
        List<String> defaultToolIds,
        List<RuntimeOptionView> engines,
        List<RuntimeOptionView> tools) {
    public RuntimeOptionsView {
        defaultToolIds = List.copyOf(defaultToolIds);
        engines = List.copyOf(engines);
        tools = List.copyOf(tools);
    }

    public static RuntimeOptionsView defaults() {
        return new RuntimeOptionsView(
                "ollama",
                List.of("search", "move"),
                List.of(
                        new RuntimeOptionView("ollama", "Ollama", true),
                        new RuntimeOptionView("openai", "OpenAI", false)),
                List.of(
                        new RuntimeOptionView("search", "Search", true),
                        new RuntimeOptionView("move", "Move", true),
                        new RuntimeOptionView("note", "Note", false)));
    }
}
