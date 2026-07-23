# Plan 007: Scoped Story Source Retrieval

## Status

Implemented.

## Issue

[GitHub #36](https://github.com/omegafrog/dnd-master/issues/36)

## Dependencies

- Plan 004

## Spec Trace

- `REQ-PKG-007`
- `REQ-RUN-003` through `REQ-RUN-010`
- `BR-006`, `BR-017`, `BR-018`
- ADR-001, ADR-002

## Outcome

Authorized callers search STORYBOOK source within exact Document and Extraction Versions, prioritizing an Active Source Context and receiving SourceSpan-grounded evidence.

## Implementation Scope

- Generalize SearchChunk projection to Extraction Version and SourceSpan references.
- Add PostgreSQL FTS plus existing pgvector hybrid retrieval.
- Add owner, Document Type, Document ID, Extraction Version, and active-context filters.
- Add one bounded package-wide fallback.
- Add SourceSpan context-read contract with adjacent spans.
- Add source preview/search diagnostics to scenario setup UI.

## Acceptance Criteria

- SQL scope filters apply before ranking.
- RULEBOOK and unselected STORYBOOK evidence cannot appear.
- Active context candidates rank before package-wide fallback.
- Every result resolves to immutable SourceSpans and locator metadata.
- No-evidence result is explicit and never silently widens authorization scope.

## Test Contract

- Policy unit: scope construction, fallback policy, evidence provenance.
- Integration: pgvector+FTS ranking and owner/type/version isolation.
- Contract: scoped search and source-context-read APIs.
- UI-to-entity E2E: source query -> scoped search adapter -> persisted chunks/spans -> evidence preview.

## Excluded

- Resolution Unit attachment.
- Runtime GM planning.
