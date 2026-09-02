# RAG-011: Native OCR Hybrid Extraction

native PDF extraction, page rendering and OCR을 port 뒤에 격리하고 페이지·영역별 native/OCR/hybrid 선택과 provenance를 제공한다.


GitHub: https://github.com/omegafrog/dnd-master/issues/184

Depends on: RAG-009

## Scope

- NativePdfPort, PageRenderPort and OcrPort
- page classification and targeted OCR
- normalized geometry, extraction method and text confidence
- explicit capability failure and page blocking

## Acceptance

- native pages are not replaced wholesale by OCR
- image-only and mixed fixtures retain coordinates and provenance
- unavailable mandatory capability cannot report success
- policy unit, adapter contract and process-CLI-to-entity e2e tests pass
