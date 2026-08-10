package com.dndmaster.ruleknowledge.domain.document.anchor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AnchorTreeBuilder {
    public AnchorSkeleton build(List<MatchedAnchor> matches) {
        List<MatchedAnchor> confirmed = matches.stream().filter(MatchedAnchor::confirmed).toList();
        Map<String, String> elementByNumbering = new HashMap<>();
        Map<String, Integer> tocLevelByElement = new HashMap<>();
        List<AnchorSkeletonNode> nodes = new ArrayList<>();
        List<MatchedAnchor> unresolved = new ArrayList<>(matches.stream().filter(match -> !match.confirmed()).toList());
        Set<String> ownedElements = new HashSet<>();
        for (MatchedAnchor match : confirmed) {
            if (!ownedElements.add(match.bodyElementId())) {
                unresolved.add(MatchedAnchor.unresolved(match.anchor(), List.of("duplicate skeleton ownership", match.bodyElementId())));
                continue;
            }
            if (!match.anchor().numbering().isBlank()) elementByNumbering.put(match.anchor().numbering(), match.bodyElementId());
            if (match.anchor().levelSuggestion() != null) tocLevelByElement.put(match.bodyElementId(), match.anchor().levelSuggestion());
        }
        for (MatchedAnchor match : confirmed) {
            if (!ownedElements.contains(match.bodyElementId()) || nodes.stream().anyMatch(node -> node.bodyElementId().equals(match.bodyElementId()))) continue;
            String parent = match.anchor().levelSuggestion() == null
                    ? parentNumbering(match.anchor().numbering()).map(elementByNumbering::get).orElse("")
                    : nearestTocParent(nodes, tocLevelByElement, match.anchor().levelSuggestion());
            nodes.add(new AnchorSkeletonNode(match.bodyElementId(), parent, match.score()));
        }
        validate(nodes);
        return new AnchorSkeleton(nodes, unresolved);
    }

    private String nearestTocParent(List<AnchorSkeletonNode> nodes, Map<String, Integer> tocLevelByElement, int level) {
        for (int index = nodes.size() - 1; index >= 0; index--) {
            AnchorSkeletonNode candidate = nodes.get(index);
            Integer candidateLevel = tocLevelByElement.get(candidate.bodyElementId());
            if (candidateLevel != null && candidateLevel < level) return candidate.bodyElementId();
        }
        return "";
    }

    private java.util.Optional<String> parentNumbering(String numbering) {
        int separator = numbering == null ? -1 : numbering.lastIndexOf('.');
        return separator < 0 ? java.util.Optional.empty() : java.util.Optional.of(numbering.substring(0, separator));
    }

    private void validate(List<AnchorSkeletonNode> nodes) {
        Set<String> ids = new HashSet<>();
        Map<String, String> parents = new HashMap<>();
        for (AnchorSkeletonNode node : nodes) {
            if (!ids.add(node.bodyElementId())) throw new IllegalArgumentException("duplicate skeleton ownership: " + node.bodyElementId());
            parents.put(node.bodyElementId(), node.parentBodyElementId());
        }
        for (String id : ids) {
            Set<String> seen = new HashSet<>();
            String current = id;
            while (!current.isBlank()) {
                if (!seen.add(current)) throw new IllegalArgumentException("skeleton cycle: " + id);
                current = parents.getOrDefault(current, "");
            }
        }
    }
}
