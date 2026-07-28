# 028-1 - 캐릭터 컨텍스트 RAG 검색

- Status: approved
- Issue: [#90](https://github.com/omegafrog/dnd-master/issues/90)
- Parent: 028
- Dependencies: none

## Outcome

RULEBOOK도 vector chunk 기반으로 검색하고, RULEBOOK/STORYBOOK/HANDOUT를 문서 유형별로 독립 검색해 typed Evidence를 반환한다.

## Scope

- 기존 RULEBOOK 청킹·임베딩 인덱스 활용 확인 및 검색 seam 추가.
- `CHARACTER_CREATION` 검색 계약과 knowledge-side application service/adapter.
- 문서 유형별 similarity threshold, bundle 문서/extraction version scope.
- 중복 제거와 token budget packing. 전역 고정 개수 제한 금지.
- Evidence에 document ID, type, extraction version, locator, quote/span, similarity 유지.

## Acceptance

- RULEBOOK vector chunk에서 캐릭터 생성 관련 근거가 반환된다.
- 한 유형 결과가 많아도 다른 유형 검색이 실행된다.
- threshold 미달 결과는 제외되고, 한 유형 결과 없음은 전체 검색 실패가 아니다.
- scope/version 밖 Evidence는 반환되지 않는다.

## Tests

- threshold, scope/version, empty-type isolation, dedupe, budget policy unit/integration tests.
- `ui ~ entity` E2E: 문서 bundle 선택 → character-context search API → 유형별 Evidence 확인.

## Implementation scope

`rule-knowledge-service` 검색 application/port/repository/controller 및 pgvector integration tests, 관련 adventure adapter contract tests.
