# Plan 004: Knowledge Document TXT SourceSpan Tracer

## Status

Implemented.

## Issue

[GitHub #33](https://github.com/omegafrog/dnd-master/issues/33)

## Dependencies

None.

## Spec Trace

- `REQ-ING-001`, `REQ-ING-007`, `REQ-ING-015`, `REQ-ING-017` through `REQ-ING-021`
- `REQ-RES-001`
- `BR-001`, `BR-005`, `BR-007`, `BR-008`
- ADR-001, ADR-002

## Outcome

Owner uploads a TXT STORYBOOK through the existing setup UI and receives a durable, immutable Extraction Version containing line/range SourceSpans and searchable chunks.

## Implementation Scope

- Generalize rulebook registration to Knowledge Document without breaking RULEBOOK compatibility.
- Introduce Extraction Version, TXT SourceSpan, ExtractionWarning, SearchChunk-to-Span references.
- Introduce `SourceObjectStoragePort` and durable local filesystem adapter.
- Introduce `WorkQueuePort` and initial Postgres lease/job adapter.
- Preserve owner-scoped byte-hash deduplication.
- Expose upload, extraction status, warnings, and source preview contracts.
- Update `RulebookSetup` document status and preview flow.

## Acceptance Criteria

- Same owner and same bytes reuse one Knowledge Document.
- Different owners may upload identical bytes independently.
- Successful TXT extraction is immutable and addressable by version.
- Line and character ranges round-trip to exact source text.
- Worker duplicate delivery does not create duplicate versions or spans.
- Partial/failure state remains visible per document.

## Test Contract

- Policy unit: ownership hash identity, terminal version immutability, locator validation, job state machine.
- Adapter integration: Postgres uniqueness/lease/crash recovery and local object storage.
- Contract: Knowledge Document upload/status/source preview OpenAPI.
- UI-to-entity E2E: browser upload -> controller -> persisted document/version/spans -> rendered status and preview.

## Excluded

- PDF, DOCX, image, OCR.
- Scenario Bundle and Resolution Unit.
