package com.dndmaster.adventure.domain.scenario;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class BasicRulebookSpellInventory {
    public static final String SOURCE_VERSION = "DND_5E_BASIC_RULES_2014";
    public static final long EXTRACTION_VERSION = 1;
    private static final String RESOURCE = "/com/dndmaster/adventure/domain/scenario/basic-rulebook-2014-spells.tsv";

    private BasicRulebookSpellInventory() {}

    public static List<StructuredSpellDefinition> load() {
        var stream = BasicRulebookSpellInventory.class.getResourceAsStream(RESOURCE);
        if (stream == null) throw new IllegalStateException("official spell inventory resource is missing");
        try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            var spells = new ArrayList<StructuredSpellDefinition>();
            String line;
            boolean headerRead = false;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) continue;
                if (!headerRead) {
                    headerRead = true;
                    continue;
                }
                String[] fields = line.split("\\t", -1);
                if (fields.length != 13) throw new IllegalStateException("invalid official spell inventory row");
                spells.add(new StructuredSpellDefinition(
                        fields[0], fields[1], fields[2], SOURCE_VERSION, EXTRACTION_VERSION,
                        fields[3], fields[4], fields[5], fields[6], fields[7], fields[8], fields[9], fields[10],
                        java.util.Arrays.stream(fields[11].split(",")).map(Integer::parseInt).toList(), fields[12],
                        false, StructuredSpellDefinition.ReviewStatus.PENDING));
            }
            if (!headerRead) throw new IllegalStateException("official spell inventory is empty");
            return List.copyOf(spells);
        } catch (IOException | NumberFormatException exception) {
            throw new IllegalStateException("could not read official spell inventory", exception);
        }
    }
}
