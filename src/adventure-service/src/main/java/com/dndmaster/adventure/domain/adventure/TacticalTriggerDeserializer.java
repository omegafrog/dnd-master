package com.dndmaster.adventure.domain.adventure;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Backfills only the absent field used by pre-038-4 persisted plans. */
public final class TacticalTriggerDeserializer extends StdDeserializer<TacticalTrigger> {
    public TacticalTriggerDeserializer() { super(TacticalTrigger.class); }

    @Override public TacticalTrigger deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        JsonNode node = parser.getCodec().readTree(parser);
        String id = node.path("id").asText(null);
        TacticalTriggerType type = TacticalTriggerType.valueOf(node.path("type").asText());
        List<String> targets = new ArrayList<>();
        JsonNode targetNode = node.get("targetIds");
        if (targetNode != null && targetNode.isArray()) targetNode.forEach(value -> targets.add(value.asText()));
        String transition = node.has("transitionId") && !node.get("transitionId").isNull() ? node.get("transitionId").asText() : "";
        PlacementGrounding grounding = parser.getCodec().treeToValue(node.get("grounding"), PlacementGrounding.class);
        if (!node.has("qualifyingAction")) return new TacticalTrigger(id, type, targets, transition, grounding,
                type.name().toLowerCase(java.util.Locale.ROOT), true);
        String action = node.get("qualifyingAction").isNull() ? null : node.get("qualifyingAction").asText();
        return new TacticalTrigger(id, type, targets, transition, grounding, action);
    }
}
