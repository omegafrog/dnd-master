package com.dndmaster.aigamemaster.application.scene;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Keeps an already executed dialogue action from being offered again immediately. */
final class SceneChoicePolicy {
    private static final Pattern CHOICE = Pattern.compile("^(\\d+)\\.\\s*(.+)$");

    private SceneChoicePolicy() { }

    static String replaceImmediateRepeat(String scene, ScenarioRequest request) {
        Set<String> executed = Stream.concat(Stream.of(request.playerAction()), request.recentActions().stream())
                .map(SceneChoicePolicy::intent)
                .filter(value -> !value.isBlank())
                .collect(java.util.stream.Collectors.toSet());
        if (executed.isEmpty()) return scene;

        List<String> lines = new ArrayList<>();
        for (String line : scene.split("\\R", -1)) {
            Matcher choice = CHOICE.matcher(line.trim());
            if (choice.matches() && executed.contains(intent(choice.group(2)))) {
                lines.add(choice.group(1) + ". 대화의 다음 조건을 협상한다.");
            } else {
                lines.add(line);
            }
        }
        return String.join("\n", lines);
    }

    private static String intent(String value) {
        if (value == null || value.isBlank()) return "";
        String normalized = value.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        String topic = topic(normalized);
        if (topic.isBlank()) return normalized.replaceAll("[^\\p{L}\\p{N}]", "");
        if (normalized.contains("ask") || normalized.contains("question") || normalized.contains("what")
                || normalized.contains("묻") || normalized.contains("질문")
                || normalized.contains("물어")) {
            return "ask:" + topic;
        }
        return normalized.replaceAll("[^\\p{L}\\p{N}]", "") + ":" + topic;
    }

    private static String topic(String normalized) {
        if (normalized.contains("reward") || normalized.contains("보상") || normalized.contains("대가")) return "reward";
        if (normalized.contains("price") || normalized.contains("값") || normalized.contains("금액")) return "price";
        if (normalized.contains("name") || normalized.contains("이름")) return "name";
        return "";
    }
}
