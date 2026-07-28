# 028 - 근거 기반 캐릭터 생성 컨텍스트 RAG

- Status: approved
- Issue: [#89](https://github.com/omegafrog/dnd-master/issues/89)
- Product Spec: `docs/specs/product-spec.md`
- Architecture Spec: `docs/specs/architecture-spec.md`
- Slices: [028-1](028-1-character-context-rag-search.md), [028-2](028-2-character-context-compilation.md), [028-3](028-3-blueprint-input-modes-conflicts.md), [028-4](028-4-character-context-e2e.md)

## Outcome

컴파일 시 RULEBOOK, STORYBOOK, HANDOUT를 독립 RAG 검색하고, 근거 기반 캐릭터 생성 블루프린트를 만든다. 고정 excerpt 개수와 전역 top-k로 한 문서 유형이 다른 유형을 밀어내지 않게 한다.

## Acceptance

- 인덱싱된 RULEBOOK의 종족·클래스·배경 선택지가 검색되어 Blueprint에 반영된다.
- 문서 유형별 유사도 하한과 token budget을 사용한다.
- 근거가 부족한 항목은 수동 입력으로 남고, 문서 간 충돌은 `CONFLICT_REVIEW`가 된다.
- 기존 Resolution Unit 추출 동작은 변경되지 않는다.
- 최종 흐름은 검색 → 추출 → 검토/수정 → 게시 → 캐릭터 시트 생성까지 통과한다.

## Test contract

- 각 slice에 정책 단위 테스트와 `ui ~ entity` E2E를 포함한다.
- 최종 E2E: indexed RULEBOOK + STORYBOOK → compile → blueprint review → publish → character sheet.

## Dependency

`028-1 → (028-2, 028-3) → 028-4`
