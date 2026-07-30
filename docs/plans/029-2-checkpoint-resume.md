# 029-2 - Checkpoint 재개

- Status: completed
- Issue: [#98](https://github.com/omegafrog/dnd-master/issues/98)
- Parent: [029](029-rulebook-progressive-embedding.md)
- Dependencies: [029-1](029-1-progressive-embedding.md)

## Outcome

중간 실패 뒤 persisted checkpoint에서 재개하고, 이미 완료된 chunk를 재임베딩하지 않는다.

## Acceptance

- completed chunk identity와 next cursor가 저장된다.
- N번째 batch 실패 후 N+1 또는 미완료 chunk부터 재개한다.
- retryable/permanent failure가 구분된다.
- 완료 batch 재호출·중복 vector가 없다.

## Tests

- policy/application unit: failure-after-N, resume, duplicate prevention.
- `ui ~ entity` E2E: retry/status boundary에서 기존 chunk entity 재사용 확인.

## Scope

checkpoint/chunk state domain, repository reload/query, retry orchestration, migration 및 테스트.
