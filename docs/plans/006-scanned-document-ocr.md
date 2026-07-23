# Plan 006: Scanned Document OCR

## Status

Implemented.

## Issue

[GitHub #35](https://github.com/omegafrog/dnd-master/issues/35)

## Dependencies

- Plan 005

## Spec Trace

- `REQ-ING-001`, `REQ-ING-009` through `REQ-ING-013`, `REQ-ING-019`, `REQ-ING-020`
- `UC-002`
- Architecture TD: OCR port and Tesseract adapter

## Outcome

Owner uploads an image, scanned PDF, or mixed PDF and receives page-level native/OCR SourceSpans with confidence, bounding boxes, warnings, and targeted retry.

## Implementation Scope

- Add `OcrPort` and Tesseract Korean/English adapter.
- Add image-document extraction.
- Render only PDF pages whose native text fails quality policy.
- Persist OCR method, confidence, boxes, rendered page Asset, and warnings.
- Support page-level timeout, retry, and `NEEDS_INPUT`.
- Expose OCR status and retry in document UI.

## Acceptance Criteria

- Native pages in mixed PDFs never receive unnecessary OCR.
- Scanned pages retain page coordinates and OCR confidence.
- OCR failure on one page preserves other pages and yields `PARTIAL`.
- Missing language pack is actionable and does not loop retries.
- Retry targets failed page/work item without duplicating successful spans.

## Test Contract

- Policy unit: native-vs-OCR selection, warning aggregation, retry eligibility.
- Golden fixtures: Korean, English, image-only, mixed PDF, low-confidence OCR.
- Adapter integration: Tesseract timeout/error mapping with fake and smoke adapter tests.
- UI-to-entity E2E: scanned upload -> OCR job -> persisted spans/warnings -> retry/status UI.

## Excluded

- External OCR provider implementation.
- Semantic image/map understanding.
