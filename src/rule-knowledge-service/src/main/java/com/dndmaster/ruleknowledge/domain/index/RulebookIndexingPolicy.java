package com.dndmaster.ruleknowledge.domain.index;

import com.dndmaster.ruleknowledge.domain.rulebook.Rulebook;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RulebookIndexingPolicy {
    public static final long AUTOMATIC_SPLIT_THRESHOLD_BYTES = 100L * 1024 * 1024;

    private static final List<HeadingPattern> DEFAULT_PATTERNS = List.of(
            new HeadingPattern(Pattern.compile("^제\\d+장\\s*:\\s*(.+)$"), "PART", "Korean chapter"),
            new HeadingPattern(Pattern.compile("^부록\\s+[A-Z]\\s*:\\s*(.+)$"), "APPENDIX", "Korean appendix"));

    private final int maximumChunkCharacters;
    private final List<HeadingPattern> headingPatterns;

    public RulebookIndexingPolicy(int maximumChunkCharacters) {
        this(maximumChunkCharacters, DEFAULT_PATTERNS);
    }

    public RulebookIndexingPolicy(int maximumChunkCharacters, List<HeadingPattern> headingPatterns) {
        if (maximumChunkCharacters <= 0) throw new IllegalArgumentException("maximum chunk size must be positive");
        this.maximumChunkCharacters = maximumChunkCharacters;
        this.headingPatterns = headingPatterns != null ? List.copyOf(headingPatterns) : DEFAULT_PATTERNS;
    }

    public RulebookIndex createIndex(Rulebook rulebook, IndexKey key, int dimension) {
        requireEligible(rulebook);
        if (!rulebook.id().equals(key.rulebookId())) {
            throw new IllegalArgumentException("index key must reference the rulebook");
        }
        return new RulebookIndex(IndexId.generate(), key, rulebook.ownerPlayerId(), dimension);
    }

    public List<RulebookChunk> createChunks(Rulebook rulebook) {
        requireEligible(rulebook);
        String content = rulebook.extractionResult().orElseThrow().content().orElseThrow();
        if (content.length() < 2) {
            throw new IllegalStateException("rulebook content is too short for chunking");
        }

        int effectiveMax = maximumChunkCharacters;
        if (requiresAutomaticSplit(rulebook) && content.length() <= maximumChunkCharacters) {
            effectiveMax = (content.length() + 1) / 2;
        }

        return structureAwareChunk(rulebook.id(), content, effectiveMax);
    }

    public boolean requiresAutomaticSplit(Rulebook rulebook) {
        return Objects.requireNonNull(rulebook, "rulebook must not be null").fileSize().bytes()
                > AUTOMATIC_SPLIT_THRESHOLD_BYTES;
    }

    private List<RulebookChunk> structureAwareChunk(
            com.dndmaster.ruleknowledge.domain.rulebook.RulebookId rulebookId,
            String content,
            int maxChunkSize) {

        String[] lines = content.split("\n", -1);
        List<StructuralSegment> segments = parseStructure(lines);

        List<RulebookChunk> chunks = new ArrayList<>();
        int sequence = 0;
        int segmentIdx = 0;

        while (segmentIdx < segments.size()) {
            StructuralSegment current = segments.get(segmentIdx);

            if (current.text.length() > maxChunkSize) {
                List<RulebookChunk> splitChunks = splitOversizedSegment(
                        rulebookId, current, maxChunkSize, sequence);
                chunks.addAll(splitChunks);
                sequence += splitChunks.size();
                segmentIdx++;
            } else {
                List<StructuralSegment> merged = mergeSegments(segments, segmentIdx, maxChunkSize);
                String mergedText = buildMergedText(merged);
                int start = merged.getFirst().startOffset;
                int end = merged.getLast().endOffset;

                UUID chunkUuid = UUID.nameUUIDFromBytes(
                        (rulebookId.value() + ":" + sequence).getBytes(StandardCharsets.UTF_8));
                chunks.add(new RulebookChunk(
                        rulebookId,
                        new ChunkId(chunkUuid),
                        sequence,
                        new ExtractedContentRange(start, end),
                        mergedText,
                        merged.getFirst().chapter,
                        merged.getFirst().section));
                sequence++;
                segmentIdx += merged.size();
            }
        }

        return List.copyOf(chunks);
    }

    private List<StructuralSegment> parseStructure(String[] lines) {
        List<StructuralSegment> segments = new ArrayList<>();
        String currentChapter = null;
        int offset = 0;

        for (String line : lines) {
            int lineStart = offset;
            int lineEnd = offset + line.length();

            String matchedChapter = matchChapterHeading(line);

            if (matchedChapter != null) {
                currentChapter = matchedChapter;
            }

            String detectedSection = detectSection(line);

            if (!segments.isEmpty()) {
                StructuralSegment last = segments.getLast();
                boolean sameSection = Objects.equals(last.chapter, currentChapter)
                        && Objects.equals(last.section, detectedSection);

                if (sameSection && last.text.length() + line.length() + 1 <= maximumChunkCharacters * 2) {
                    segments.set(segments.size() - 1,
                            new StructuralSegment(
                                    last.chapter,
                                    last.section,
                                    last.text + "\n" + line,
                                    last.startOffset,
                                    lineEnd));
                    offset = lineEnd + 1;
                    continue;
                }
            }

            segments.add(new StructuralSegment(
                    currentChapter,
                    detectedSection,
                    line,
                    lineStart,
                    lineEnd));
            offset = lineEnd + 1;
        }

        return segments;
    }

    private String matchChapterHeading(String line) {
        for (HeadingPattern hp : headingPatterns) {
            Matcher m = hp.compiledPattern().matcher(line);
            if (m.find()) {
                return hp.groupName() + ": " + m.group(1).trim();
            }
        }
        return null;
    }

    private String detectSection(String line) {
        String trimmed = line.strip();
        Matcher numbered = Pattern.compile("^\\d+\\.\\s+(.+)$").matcher(trimmed);
        if (numbered.find() && trimmed.length() < 80) {
            return numbered.group(1).trim();
        }
        return null;
    }

    private List<StructuralSegment> mergeSegments(
            List<StructuralSegment> segments, int startIdx, int maxChunkSize) {

        StructuralSegment first = segments.get(startIdx);
        List<StructuralSegment> merged = new ArrayList<>();
        merged.add(first);
        int totalLength = first.text.length();

        for (int i = startIdx + 1; i < segments.size(); i++) {
            StructuralSegment next = segments.get(i);
            if (totalLength + next.text.length() + 1 > maxChunkSize) {
                break;
            }
            merged.add(next);
            totalLength += next.text.length() + 1;
        }

        return merged;
    }

    private String buildMergedText(List<StructuralSegment> segments) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < segments.size(); i++) {
            if (i > 0) sb.append('\n');
            sb.append(segments.get(i).text);
        }
        return sb.toString();
    }

    private List<RulebookChunk> splitOversizedSegment(
            com.dndmaster.ruleknowledge.domain.rulebook.RulebookId rulebookId,
            StructuralSegment segment,
            int maxChunkSize,
            int baseSequence) {

        List<RulebookChunk> chunks = new ArrayList<>();
        String text = segment.text;
        int globalOffset = segment.startOffset;
        int remaining = text.length();
        int localSequence = 0;

        while (remaining > 0) {
            int chunkLen = Math.min(maxChunkSize, remaining);
            int splitPoint = findParagraphSplit(text, text.length() - remaining, chunkLen);

            String chunkText = text.substring(text.length() - remaining, text.length() - remaining + splitPoint);
            int start = globalOffset + (text.length() - remaining);
            int end = start + chunkText.length();

            UUID chunkUuid = UUID.nameUUIDFromBytes(
                    (rulebookId.value() + ":" + (baseSequence + localSequence))
                            .getBytes(StandardCharsets.UTF_8));
            chunks.add(new RulebookChunk(
                    rulebookId,
                    new ChunkId(chunkUuid),
                    baseSequence + localSequence,
                    new ExtractedContentRange(start, end),
                    chunkText,
                    segment.chapter,
                    segment.section));

            remaining -= splitPoint;
            localSequence++;
        }

        return chunks;
    }

    private int findParagraphSplit(String text, int offset, int maxLen) {
        int end = Math.min(text.length(), offset + maxLen);
        if (end == text.length()) {
            return end - offset;
        }

        int lastParagraph = text.lastIndexOf("\n\n", offset + maxLen);
        if (lastParagraph > offset) {
            return lastParagraph - offset;
        }

        int lastNewline = text.lastIndexOf('\n', offset + maxLen);
        if (lastNewline > offset) {
            return lastNewline - offset;
        }

        int lastSpace = text.lastIndexOf(' ', offset + maxLen);
        if (lastSpace > offset) {
            return lastSpace - offset;
        }

        return maxLen;
    }

    private static void requireEligible(Rulebook rulebook) {
        if (!Objects.requireNonNull(rulebook, "rulebook must not be null").isEligibleForSplitting()) {
            throw new IllegalStateException("rulebook is not eligible for indexing");
        }
    }

    private record StructuralSegment(
            String chapter,
            String section,
            String text,
            int startOffset,
            int endOffset) {}
}
