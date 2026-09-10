package com.dndmaster.aigamemaster.application.scene;

import com.dndmaster.aigamemaster.application.rule.SourceEvidence;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class SceneCitationGrounder {
    private static final Pattern CITATION = Pattern.compile("^\\[E(\\d+)]\\s*");
    private static final Pattern RUNTIME_FACT = Pattern.compile("^\\[RUNTIME_FACT]\\s*", Pattern.CASE_INSENSITIVE);
    private static final Pattern RUNTIME = Pattern.compile("^\\[(?:RUNTIME|NPC)]\\s*", Pattern.CASE_INSENSITIVE);
    private static final Pattern CANONICAL_CLAIM = Pattern.compile(
            "(범인|비밀|숨겨진|숨은|원인|진상|단서|정답|해답|culprit|secret|hidden|cause|clue|solution)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern RUNTIME_REACTION = Pattern.compile(
            "(말하|대답|묻|질문|협상|제안|거절|침묵|시선|고개|웃|한숨|모르|조건|요구|동의|거부|said|ask|answer|offer|refus|negotiat|reply)",
            Pattern.CASE_INSENSITIVE);

    private SceneCitationGrounder() { }

    static String groundOrFallback(String scene, List<SourceEvidence> evidence) {
        return groundOrFallback(scene, evidence, false, false);
    }

    static String groundOrFallback(String scene, ScenarioRequest request, boolean runtimeInteraction) {
        return groundOrFallback(scene, request.evidence(), runtimeInteraction,
                request.playerAction() != null && !request.playerAction().isBlank());
    }

    private static String groundOrFallback(String scene, List<SourceEvidence> evidence,
            boolean runtimeInteraction, boolean dialogueAction) {
        StringBuilder grounded = new StringBuilder();
        for (String line : scene.split("\\R")) {
            if (line.isBlank()) continue;
            String trimmed = line.trim();
            Matcher runtimeFact = RUNTIME_FACT.matcher(trimmed);
            if (runtimeFact.find()) {
                String text = trimmed.substring(runtimeFact.end()).trim();
                // A persisted runtime fact is a reaction/detail, never a
                // numbered choice. It also requires an executed dialogue
                // action in the current request.
                if (!dialogueAction || text.matches("^\\d+\\.\\s+.*")
                        || !runtimeLineIsSafe(text, true)) {
                    return fallback(dialogueAction);
                }
                append(grounded, text);
                continue;
            }
            Matcher runtime = RUNTIME.matcher(trimmed);
            if (runtime.find()) {
                String text = trimmed.substring(runtime.end()).trim();
                boolean numberedChoice = text.matches("^\\d+\\.\\s+.*");
                if ((!runtimeInteraction && !numberedChoice && !dialogueAction)
                        || !runtimeLineIsSafe(text, dialogueAction)) {
                    return fallback(dialogueAction);
                }
                append(grounded, text);
                continue;
            }
            Matcher citation = CITATION.matcher(trimmed);
            if (!citation.find()) return fallback(runtimeInteraction && dialogueAction);
            int evidenceIndex = Integer.parseInt(citation.group(1));
            if (evidenceIndex < 1 || evidenceIndex > evidence.size()) return fallback(runtimeInteraction && dialogueAction);
            append(grounded, trimmed.substring(citation.end()).trim());
        }
        return grounded.isEmpty() ? fallback(runtimeInteraction && dialogueAction) : grounded.toString();
    }

    private static boolean runtimeLineIsSafe(String text, boolean dialogueAction) {
        if (text.isBlank() || CANONICAL_CLAIM.matcher(text.toLowerCase(Locale.ROOT)).find()) return false;
        // Numbered choices are prospective actions and remain valid even on
        // an opening turn with no already-executed dialogue. Unnumbered
        // runtime prose must be a reaction to an executed dialogue action.
        if (text.matches("^\\d+\\.\\s+.*")) return true;
        return dialogueAction && (text.contains("\"") || text.contains("“") || text.contains("”")
                || RUNTIME_REACTION.matcher(text).find());
    }

    private static void append(StringBuilder target, String value) {
        if (value.isBlank()) return;
        if (!target.isEmpty()) target.append('\n');
        target.append(value);
    }

    private static String fallback(boolean dialogueAction) {
        if (dialogueAction) {
            return "상대는 잠시 침묵하다가 시선을 피합니다. \"그건 나도 확답할 수 없네. 먼저 자네들이 원하는 조건을 말해보게.\"\n\n"
                    + "1. 원하는 조건을 제안한다.\n2. 새로운 질문을 한다.\n3. 대화를 멈추고 주변을 살핀다.";
        }
        return "주변은 잠잠하고, 당장 확인할 수 있는 변화는 없습니다.\n\n"
                + "1. 주변을 살핀다.\n2. 조심스럽게 접근한다.\n3. 일행과 대응 방법을 상의한다.";
    }
}
