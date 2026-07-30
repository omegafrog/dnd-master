package com.dndmaster.aigamemaster.application.scene;

import com.dndmaster.aigamemaster.application.rule.SourceEvidence;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class SceneCitationGrounder {
    private static final Pattern CITATION = Pattern.compile("^\\[E(\\d+)]\\s*");

    private SceneCitationGrounder() { }

    static String groundOrFallback(String scene, List<SourceEvidence> evidence) {
        StringBuilder grounded = new StringBuilder();
        for (String line : scene.split("\\R")) {
            if (line.isBlank()) continue;
            Matcher citation = CITATION.matcher(line.trim());
            if (!citation.find()) return fallback();
            int evidenceIndex = Integer.parseInt(citation.group(1));
            if (evidenceIndex < 1 || evidenceIndex > evidence.size()) return fallback();
            if (!grounded.isEmpty()) grounded.append('\n');
            grounded.append(line.trim().substring(citation.end()).trim());
        }
        return grounded.isEmpty() ? fallback() : grounded.toString();
    }

    private static String fallback() {
        return "선택한 근거 자료에서 위협이 확인되었습니다. 세부 상황은 아직 확인되지 않았습니다.\n\n"
                + "1. 주변을 살핀다.\n2. 조심스럽게 접근한다.\n3. 일행과 대응 방법을 상의한다.";
    }
}
