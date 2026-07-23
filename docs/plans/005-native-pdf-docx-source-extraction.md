# Plan 005: Native PDF/DOCX Source Extraction

## Status

Approved. In progress.

## Issue

[GitHub #34](https://github.com/omegafrog/dnd-master/issues/34)

## Dependencies

- Plan 004

## Spec Trace

- `REQ-ING-009`, `REQ-ING-012` through `REQ-ING-022`
- `REQ-RES-003`, `REQ-RES-004`
- `UC-002`

## Outcome

Owner uploads native-text PDF or DOCX and can inspect versioned SourceSpans preserving pages, coordinates, sections, paragraphs, tables, cells, boxes, assets, reading order, and partial warnings.

## Implementation Scope

- Add PDFBox native-text/layout extraction.
- Add POI DOCX section/block/table/cell extraction.
- Add SourceSpan kinds, parent/neighbor edges, normalized bounding boxes, reading order.
- Extract embedded images as immutable Assets.
- Isolate page/element failures and preserve successful spans.
- Extend document status/preview UI with page, section, asset, and warning details.

## Acceptance Criteria

- PDF locators identify page and normalized coordinates.
- DOCX locators identify section and structural path.
- Table cells and boxed text are independently referenceable.
- Reading order and parent/neighbor links are deterministic.
- One failed page or element yields `PARTIAL`, not whole-document failure.
- Re-extraction produces a new immutable Extraction Version.

## Test Contract

- Policy unit: locator type invariants, parent/neighbor integrity, partial-state aggregation.
- Golden fixtures: PDF paragraphs/columns/boxes/images and DOCX sections/tables/cells.
- Adapter integration: extracted Assets and spans persist atomically per version.
- UI-to-entity E2E: upload PDF/DOCX -> extraction -> persisted layout spans -> preview and warnings.

## Excluded

- OCR and image-only documents.
- Resolution extraction.
