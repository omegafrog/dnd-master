package com.dndmaster.ruleknowledge.domain.extraction;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ContentRoleClassificationPolicy {
    public Classification classify(DocumentNode node, String documentType) {
        if (node == null) throw new NullPointerException("node must not be null");
        String type = documentType == null ? "" : documentType.toUpperCase(Locale.ROOT);
        if (node.type() == DocumentNodeType.UNKNOWN || node.text().isBlank()) {
            return new Classification(List.of(ContentRole.RAW), List.of(new ExtractionWarning(
                    "AMBIGUOUS_STRUCTURE", ExtractionWarning.Severity.WARNING,
                    "structure could not be classified; preserved as raw content")));
        }
        List<ContentRole> roles = new ArrayList<>();
        String text = node.text().toLowerCase(Locale.ROOT);
        if (type.equals("STORYBOOK")) {
            roles.add(ContentRole.KNOWLEDGE);
            if (containsAny(text, "map", "image", "asset", "지도", "그림", "맵")) roles.add(ContentRole.GAME_ASSET);
            roles.add(ContentRole.GM_MATERIAL);
        } else if (containsAny(text, "map", "image", "asset", "지도", "그림", "맵")) {
            roles.add(ContentRole.GAME_ASSET);
        } else {
            roles.add(ContentRole.KNOWLEDGE);
        }
        return new Classification(List.copyOf(roles), List.of());
    }

    private static boolean containsAny(String text, String... values) {
        for (String value : values) if (text.contains(value)) return true;
        return false;
    }

    public record Classification(List<ContentRole> roles, List<ExtractionWarning> warnings) {
        public Classification { roles = List.copyOf(roles); warnings = List.copyOf(warnings); }
    }
}
