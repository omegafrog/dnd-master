# 033-4 — Safe Story RAG Visibility Projection

- Status: `pending`
- Issue: [#129](https://github.com/omegafrog/dnd-master/issues/129)
- Parent: [#125](https://github.com/omegafrog/dnd-master/issues/125)
- Dependencies: [033-2](033-2-retrieval-evaluation-corpus.md)
- Spec: GM quality/RAG/fine-tuning plan §Phase 3

## Outcome

Public story continuity reaches narration while GM-only and conditionally hidden facts remain protected.

## Vertical Scope

- Add chunk-level visibility: PLAYER_VISIBLE, GM_ONLY, NPC_PRIVATE, REVEALED_AFTER_EVENT, DISCOVERED, PUBLIC_SUMMARY.
- Validate disclosure conditions and reveal timing.
- Build `PlayerVisibleStoryEvidence` projection.
- Add misclassification and leakage regression corpus.

## Policy Unit Tests

- GM-only and unrevealed evidence cannot enter player-visible projection.
- Reveal event/time makes only eligible evidence visible.
- Public projection preserves document, locator, and version provenance.

## Integration And Contract Tests

- Story search request/response carries visibility and disclosure metadata.
- Runtime turn persists internal evidence while emitting only allowed projection.

## UI ~ Entity E2E

Discover/reveal story event → next GM narration → player output; verify continuity improves without secret leakage.

## Implementation Scope

Visibility domain, persistence/contracts, story retrieval filtering, projection, runtime integration, and regression tests.

## Out Of Scope

Hybrid search and reranking.

## Completion

Zero GM-only delivery, public-story Recall@5 ≥95%, NPC continuity ≥95%, leak rate 0%.
