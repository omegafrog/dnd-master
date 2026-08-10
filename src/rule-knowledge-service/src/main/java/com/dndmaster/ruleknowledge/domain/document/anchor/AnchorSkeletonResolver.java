package com.dndmaster.ruleknowledge.domain.document.anchor;

import com.dndmaster.ruleknowledge.domain.document.evidence.StructuralEvidenceExtractionResult;
import com.dndmaster.ruleknowledge.domain.document.normalized.NormalizedDocument;
import java.util.List;

/** Pure domain orchestration for Milestone 1 shadow skeleton publication. */
public final class AnchorSkeletonResolver {
    private final AnchorBuilder builder;
    private final AnchorMatcher matcher;
    private final AnchorTreeBuilder treeBuilder;
    private final PageLocatorResolver locatorResolver;

    public AnchorSkeletonResolver() {
        this(new AnchorBuilder(), new AnchorMatcher(), new AnchorTreeBuilder(), new PageLocatorResolver());
    }

    AnchorSkeletonResolver(AnchorBuilder builder, AnchorMatcher matcher, AnchorTreeBuilder treeBuilder,
                           PageLocatorResolver locatorResolver) {
        this.builder = builder;
        this.matcher = matcher;
        this.treeBuilder = treeBuilder;
        this.locatorResolver = locatorResolver;
    }

    public AnchorSkeletonResolution resolve(NormalizedDocument document, StructuralEvidenceExtractionResult evidence) {
        List<StructuralAnchor> anchors = evidence.navigationEntries().stream().map(builder::fromNavigation).toList();
        List<MatchedAnchor> matches = anchors.stream()
                .map(anchor -> matcher.match(anchor, document.elements(), locatorResolver.direct())).toList();
        AnchorSkeleton skeleton = treeBuilder.build(matches);
        List<String> diagnostics = skeleton.unresolved().isEmpty() ? List.of()
                : List.of("unresolved anchors=" + skeleton.unresolved().size());
        return new AnchorSkeletonResolution(anchors, matches, skeleton, diagnostics);
    }
}
