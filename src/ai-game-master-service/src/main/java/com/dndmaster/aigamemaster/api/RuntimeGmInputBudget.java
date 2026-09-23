package com.dndmaster.aigamemaster.api;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Conservative preflight: each UTF-8 byte consumes one estimated input token. */
final class RuntimeGmInputBudget {
    private RuntimeGmInputBudget() { }

    static int inputLimit(int contextLimit) {
        if (contextLimit <= 0) throw new IllegalArgumentException("model context limit must be positive");
        return (int) (contextLimit * 0.8);
    }

    static List<String> selectRecent(int contextLimit, String fixed, String memory, String summary,
            List<String> recent, String current) {
        int budget = inputLimit(contextLimit);
        int fixedSize = size(fixed);
        int memorySize = size(memory);
        int summarySize = size(summary);
        int currentSize = size(current);
        if (fixedSize > budget * 30L / 100 || memorySize > budget * 10L / 100
                || summarySize > budget * 15L / 100 || currentSize > budget - fixedSize - memorySize - summarySize) {
            throw new InputTooLargeException();
        }
        int recentLimit = Math.min((int) (budget * 20L / 100),
                budget - fixedSize - memorySize - summarySize - currentSize);
        List<List<String>> turns = groupedTurns(recent);
        ArrayList<String> selected = new ArrayList<>();
        int used = 0;
        boolean pending = !recent.isEmpty() && recent.get(recent.size() - 1).startsWith("PLAYER: ");
        int requiredTurns = pending && turns.size() > 1 ? 2 : Math.min(1, turns.size());
        for (int i = turns.size() - 1; i >= 0 && turns.size() - i <= 2; i--) {
            List<String> turn = turns.get(i);
            int turnSize = turn.stream().mapToInt(RuntimeGmInputBudget::size).sum();
            if (used + turnSize > recentLimit) {
                if (turns.size() - i <= requiredTurns) throw new InputTooLargeException();
                break;
            }
            selected.addAll(0, turn);
            used += turnSize;
        }
        return List.copyOf(selected);
    }

    private static List<List<String>> groupedTurns(List<String> recent) {
        List<List<String>> turns = new ArrayList<>();
        List<String> current = null;
        for (String entry : recent) {
            if (current == null || entry.startsWith("PLAYER: ") || !entry.startsWith("AI_GAME_MASTER: ")) {
                current = new ArrayList<>();
                turns.add(current);
            }
            current.add(entry);
        }
        return turns;
    }

    private static int size(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }

    static final class InputTooLargeException extends org.springframework.web.server.ResponseStatusException {
        InputTooLargeException() {
            super(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "required GM input exceeds the configured model context limit");
        }
    }
}
