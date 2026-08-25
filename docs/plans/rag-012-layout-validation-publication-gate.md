# RAG-012 Layout Validation Publication Gate

- 상태: `planned`
- 의존성: RAG-010, RAG-011
- Product Spec: UC-05, UC-07; BR-13 ~ BR-16, BR-19
- Architecture Spec: Sections 3.6 ~ 3.8, 7, 11
- GitHub Issue: [#185](https://github.com/omegafrog/dnd-master/issues/185)

## 구현 목적

레이아웃 추출기가 만든 결과를 그대로 신뢰하지 않고 모든 페이지를 렌더링 evidence와 검증한다. 텍스트, 블록 유형, 열 구조, 읽기 순서, 제목 연결, 표 구조의 confidence를 분리하고, 핵심 축 하나라도 실패하면 해당 페이지와 Extraction Version 게시를 차단해 불완전한 문서가 chunk·index로 전달되지 않게 한다.

## 사용자·엔티티 흐름

```text
structured PageExtraction + render evidence
→ deterministic LayoutValidationService
→ high-risk SecondaryLayoutValidatorPort
→ ConfidenceVector + findings
→ page VALIDATED or NEEDS_REVIEW
→ ExtractionVersion publish or block
```

## 범위

- `ConfidenceVector`와 critical-axis policy
- block coverage, overlap, clipping, column boundary, order continuity 검증
- heading association, table header/row/cell 구조 검증
- 모든 페이지 render evidence 확인
- 다열/혼합/표/OCR/ambiguity/near-threshold 고위험 판별
- `SecondaryLayoutValidatorPort` 계약과 unavailable failure
- Page Review Gate와 all-pages READY publication invariant
- non-READY에서 tree/chunk exporter 호출 차단

## 수용 기준

- 모든 페이지 결과에 render validation evidence가 있다.
- high-risk page는 2차 검증 결과가 없으면 VALIDATED가 될 수 없다.
- 각 confidence axis가 독립적으로 저장되고 평균이 failing axis를 덮지 않는다.
- critical axis 하나의 기준 미달만으로 페이지가 `NEEDS_REVIEW`가 된다.
- `NEEDS_REVIEW` 페이지가 하나라도 있으면 ready manifest, `ParsedDocument` projection, `chunks.jsonl`이 생성되지 않는다.
- 모든 페이지가 통과하면 기존 tree/chunk flow가 정상 실행된다.

## 테스트 계약

- 정책 단위 테스트: axis thresholds, high-risk classification, all-page publish invariant
- validator 계약 테스트: deterministic/secondary findings와 unavailable behavior
- CLI ~ entity e2e: multi-page PDF 중 한 페이지 실패 → version `NEEDS_REVIEW`, diagnostics만 생성
- 성공 e2e: mixed/table/OCR pages 모두 검증 → READY → existing chunks
- 회귀 테스트: chunk validator와 layout validator 결과를 혼동하지 않음

## 구현 범위

허용: layout validation/confidence services, secondary port, aggregate gate, pipeline/exporter, manifest/schema, fixtures와 테스트.

금지: 실패 페이지 자동 retry, manual review UI, backend publication/API 변경.

## 완료 증거

- per-axis confidence와 finding artifact
- READY/NEEDS_REVIEW 양쪽 process 결과
- chunk publication gate 테스트
