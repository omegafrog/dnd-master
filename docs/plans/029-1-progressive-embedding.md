# 029-1 - 점진 임베딩 배치

- Status: completed
- Issue: [#99](https://github.com/omegafrog/dnd-master/issues/99)
- Parent: [029](029-rulebook-progressive-embedding.md)
- Dependencies: none

## Outcome

전체 청크를 한 번에 Ollama에 전달하지 않고 bounded batch마다 staging vector와 progress를 저장한다. active 검색 인덱스는 전체 완료 전 안정성을 유지한다.

## Acceptance

- Provider 요청은 설정된 batch limit을 넘지 않는다.
- batch 성공마다 staging chunk와 completed count가 함께 commit된다.
- 전체 완료 시에만 active index가 READY로 승격된다.

## Tests

- policy/application unit: batch limit, batch 저장, count, final publish.
- `ui ~ entity` E2E: upload/status boundary에서 Postgres chunk 저장 확인.

## Scope

`rule-knowledge-service` indexing application/domain/port/repository, migration 보강, 관련 unit/integration tests.
