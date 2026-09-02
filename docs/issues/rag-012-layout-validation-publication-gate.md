# RAG-012: Layout Validation Publication Gate

모든 페이지를 렌더링 evidence와 검증하고 다차원 confidence 및 고위험 2차 검증으로 페이지와 Extraction Version 게시를 안전하게 차단한다.


GitHub: https://github.com/omegafrog/dnd-master/issues/185

Depends on: RAG-010, RAG-011

## Scope

- deterministic layout validation and ConfidenceVector
- high-risk page classification and SecondaryLayoutValidatorPort
- Page Review Gate and all-pages publication invariant
- READY-only ParsedDocument/chunk export

## Acceptance

- every page has render validation evidence
- one failing critical axis yields NEEDS_REVIEW regardless of averages
- high-risk pages require second validation
- a blocked page prevents READY and chunks
- policy unit, validator contract and process-CLI-to-entity e2e tests pass
