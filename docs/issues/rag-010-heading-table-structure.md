# RAG-010: Heading and Table Structure

제목과 표를 prose로 평탄화하지 않고 시각적 위계, 영역 관계, 헤더·행·셀 및 좌표를 가진 구조로 보존한다.


GitHub: https://github.com/omegafrog/dnd-master/issues/183

Depends on: RAG-009

## Scope

- heading hierarchy and association
- table header/row/cell geometry
- merged and uncertain cell representation
- projection compatibility with existing tree/chunk pipeline

## Acceptance

- headings associate only with valid same-column/spanning content
- table cells remain traceable to page coordinates
- irregular tables expose uncertainty instead of silent flattening
- policy unit, schema contract and process-CLI-to-entity e2e tests pass
