package com.dndmaster.ruleknowledge.domain.document.chunk;

import com.dndmaster.ruleknowledge.domain.document.hierarchy.CanonicalDocumentTree;
import com.dndmaster.ruleknowledge.domain.document.hierarchy.HierarchyEdge;
import com.dndmaster.ruleknowledge.domain.document.hierarchy.ResolutionStatus;
import com.dndmaster.ruleknowledge.domain.document.normalized.NormalizedDocument;
import com.dndmaster.ruleknowledge.domain.document.normalized.NormalizedElement;
import java.util.ArrayList;
import java.util.List;

/** Creates chunks only from confirmed canonical relations. */
public final class HierarchyAwareChunker {
    private final int maximumCharacters;
    public HierarchyAwareChunker(int maximumCharacters) { if (maximumCharacters < 1) throw new IllegalArgumentException("maximumCharacters must be positive"); this.maximumCharacters = maximumCharacters; }
    public List<RagChunk> createChunks(NormalizedDocument document, CanonicalDocumentTree tree) {
        List<RagChunk> result = new ArrayList<>();
        StringBuilder text = new StringBuilder(); List<String> ids = new ArrayList<>(); List<String> path = null;
        int firstPage = 0, lastPage = 0; double confidence = 1; int sequence = 0;
        for (NormalizedElement element : document.elements()) {
            HierarchyEdge edge = tree.edgeFor(element.id()).orElse(null);
            if (edge == null) continue;
            List<String> elementPath = edge.status() == ResolutionStatus.CONFIRMED ? tree.semanticPath(element.id()) : List.of();
            if (text.length() > 0 && (!elementPath.equals(path) || text.length() + element.text().length() + 1 > maximumCharacters)) {
                result.add(chunk(sequence++, text, path, ids, firstPage, lastPage, confidence)); text = new StringBuilder(); ids = new ArrayList<>(); firstPage = 0; lastPage = 0; confidence = 1;
            }
            path = elementPath;
            for (String part : split(element.text())) {
                if (text.length() > 0 && text.length() + part.length() + 1 > maximumCharacters) {
                    result.add(chunk(sequence++, text, path, ids, firstPage, lastPage, confidence)); text = new StringBuilder(); ids = new ArrayList<>(); firstPage = 0; lastPage = 0; confidence = 1;
                }
                if (text.length() > 0) text.append('\n'); text.append(part); ids.add(element.id());
                firstPage = firstPage == 0 ? element.page() : Math.min(firstPage, element.page()); lastPage = Math.max(lastPage, element.page()); confidence = Math.min(confidence, edge.confidence());
            }
        }
        if (text.length() > 0) result.add(chunk(sequence, text, path, ids, firstPage, lastPage, confidence));
        return List.copyOf(result);
    }
    private RagChunk chunk(int sequence, StringBuilder text, List<String> path, List<String> ids, int firstPage, int lastPage, double confidence) {
        return new RagChunk("rag:" + sequence, text.toString(), path == null ? List.of() : path, ids, firstPage, lastPage, confidence);
    }
    private List<String> split(String value) {
        if (value.length() <= maximumCharacters) return List.of(value);
        List<String> parts = new ArrayList<>(); for (int start = 0; start < value.length(); start += maximumCharacters) parts.add(value.substring(start, Math.min(value.length(), start + maximumCharacters))); return parts;
    }
}
