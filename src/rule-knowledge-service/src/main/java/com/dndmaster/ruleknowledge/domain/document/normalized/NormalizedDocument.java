package com.dndmaster.ruleknowledge.domain.document.normalized;

import java.util.List;

/** Immutable, parser-independent extraction boundary. Unknown engine fields stay in adapter diagnostics. */
public record NormalizedDocument(String schemaVersion, String extractor, String extractorVersion,
                                 String sourceIdentity, List<NormalizedPage> pages,
                                 List<NormalizedElement> elements, List<NormalizedTable> tables,
                                 List<NormalizedPicture> pictures, List<NormalizedOutlineEntry> outlines,
                                 List<NormalizedParserRelation> parserRelations, List<NormalizedWarning> warnings, String rawText) {
    public NormalizedDocument(String schemaVersion, String extractor, String extractorVersion, String sourceIdentity,
                              List<NormalizedPage> pages, List<NormalizedElement> elements, List<NormalizedTable> tables,
                              List<NormalizedPicture> pictures, List<NormalizedOutlineEntry> outlines,
                              List<NormalizedParserRelation> parserRelations, String rawText) {
        this(schemaVersion, extractor, extractorVersion, sourceIdentity, pages, elements, tables, pictures, outlines,
                parserRelations, List.of(), rawText);
    }

    public NormalizedDocument {
        schemaVersion = required(schemaVersion, "schemaVersion");
        extractor = required(extractor, "extractor");
        extractorVersion = required(extractorVersion, "extractorVersion");
        sourceIdentity = required(sourceIdentity, "sourceIdentity");
        pages = copy(pages); elements = copy(elements); tables = copy(tables); pictures = copy(pictures);
        outlines = copy(outlines); parserRelations = copy(parserRelations); warnings = copy(warnings); rawText = rawText == null ? "" : rawText;
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }
    private static <T> List<T> copy(List<T> value) { return value == null ? List.of() : List.copyOf(value); }
}
