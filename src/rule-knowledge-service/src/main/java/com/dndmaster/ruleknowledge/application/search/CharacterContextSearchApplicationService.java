package com.dndmaster.ruleknowledge.application.search;

import com.dndmaster.ruleknowledge.application.indexing.EmbeddingPort;
import com.dndmaster.ruleknowledge.domain.index.*;
import com.dndmaster.ruleknowledge.domain.rulebook.*;
import java.util.*;

public final class CharacterContextSearchApplicationService {
    private final CharacterContextSearchPort searchPort;
    private final EmbeddingPort embeddingPort;
    private final String embeddingModel;
    private final int embeddingDimension;

    public CharacterContextSearchApplicationService(
            CharacterContextSearchPort searchPort, EmbeddingPort embeddingPort, int embeddingDimension) {
        this(searchPort, embeddingPort, "character-context", embeddingDimension);
    }

    public CharacterContextSearchApplicationService(
            CharacterContextSearchPort searchPort, EmbeddingPort embeddingPort, String embeddingModel, int embeddingDimension) {
        this.searchPort = Objects.requireNonNull(searchPort);
        this.embeddingPort = Objects.requireNonNull(embeddingPort);
        this.embeddingModel = Objects.requireNonNull(embeddingModel);
        if (embeddingDimension <= 0) throw new IllegalArgumentException("embedding dimension must be positive");
        this.embeddingDimension = embeddingDimension;
    }

    public List<CharacterContextEvidence> search(CharacterContextSearchQuery query) {
        Objects.requireNonNull(query);
        RulebookChunk input = new RulebookChunk(
                RulebookId.generate(), new ChunkId(UUID.randomUUID()), 0,
                new ExtractedContentRange(0, query.situation().length()), query.situation(), null, null);
        float[] embedding = embeddingPort.embed(List.of(input), embeddingModel, embeddingDimension).getFirst().vector();
        Map<DocumentType, List<CharacterContextEvidence>> byType = new EnumMap<>(DocumentType.class);
        for (DocumentType type : DocumentType.values()) {
            List<CharacterContextDocumentScope> scope = query.scope().getOrDefault(type, List.of());
            if (scope.isEmpty()) continue;
            double threshold = query.thresholds().getOrDefault(type, 0d);
            List<String> chapterHints = type == DocumentType.RULEBOOK ? characterCreationChapterHints(query.situation()) : List.of();
            List<String> sectionHints = type == DocumentType.RULEBOOK ? characterCreationSectionHints(query.situation()) : List.of();
            List<CharacterContextEvidence> candidates = searchPort.search(query.owner(), type, scope, embedding, chapterHints, sectionHints).stream()
                    .filter(hit -> hit.documentType() == type && hit.similarity() >= threshold)
                    .sorted(Comparator.comparingDouble(CharacterContextSearchHit::similarity).reversed())
                    .map(CharacterContextSearchHit::toEvidence)
                    .toList();
            byType.put(type, candidates);
        }
        return pack(byType, query.tokenBudget());
    }

    private static List<String> characterCreationSectionHints(String situation) {
        String normalized = situation.toLowerCase(Locale.ROOT);
        if (normalized.contains("종족") || normalized.contains("race")) return List.of("종족 선택", "하위종족");
        if (normalized.contains("직업") || normalized.contains("class")) return List.of("직업 선택", "시작 장비");
        if (normalized.contains("배경") || normalized.contains("신념") || normalized.contains("개성")) return List.of("개성과 배경", "특성");
        return List.of("선택", "하위종족", "시작 장비");
    }

    private static List<String> characterCreationChapterHints(String situation) {
        String normalized = situation.toLowerCase(Locale.ROOT);
        if (!normalized.contains("character") && !normalized.contains("캐릭터")
                && !normalized.contains("작성") && !normalized.contains("생성")) return List.of();
        if (normalized.contains("starting equipment") || normalized.contains("initial equipment")
                || normalized.contains("초기 장비") || normalized.contains("시작 장비")
                || normalized.contains("직업") || normalized.contains("class")) return List.of("클래스");
        if (normalized.contains("race") || normalized.contains("species") || normalized.contains("종족")) return List.of("종족");
        if (normalized.contains("background") || normalized.contains("ideal") || normalized.contains("belief")
                || normalized.contains("배경") || normalized.contains("신념") || normalized.contains("개성")) return List.of("개성과 배경");
        return List.of("캐릭터 제작 순서", "캐릭터 제작 선택", "능력 점수 사용하기",
                "종족", "클래스", "개성과 배경");
    }

    private static List<CharacterContextEvidence> pack(
            Map<DocumentType, List<CharacterContextEvidence>> byType, int budget) {
        if (budget == 0) {
            return byType.values().stream().flatMap(List::stream)
                    .filter(candidate -> candidate != null)
                    .collect(java.util.stream.Collectors.toMap(
                            CharacterContextSearchApplicationService::key,
                            candidate -> candidate,
                            (first, ignored) -> first,
                            LinkedHashMap::new))
                    .values().stream().toList();
        }
        List<CharacterContextEvidence> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int used = 0;
        int index = 0;
        boolean added;
        do {
            added = false;
            for (List<CharacterContextEvidence> candidates : byType.values()) {
                if (index >= candidates.size()) continue;
                CharacterContextEvidence candidate = candidates.get(index);
                if (!seen.add(key(candidate))) continue;
                int tokens = Math.max(1, (candidate.excerpt().length() + 3) / 4);
                if (used + tokens <= budget) {
                    result.add(candidate);
                    used += tokens;
                }
                added = true;
            }
            index++;
        } while (added);
        return List.copyOf(result);
    }

    private static String key(CharacterContextEvidence evidence) {
        return evidence.documentId().value() + ":" + evidence.extractionVersion() + ":" + evidence.locator();
    }

    private static String key(CharacterContextSearchHit hit) {
        return hit.documentId().value() + ":" + hit.extractionVersion() + ":" + hit.locator();
    }
}
