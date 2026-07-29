# 029-4 - 진행 상태 API

- Status: completed
- Issue: [#95](https://github.com/omegafrog/dnd-master/issues/95)
- Parent: [029](029-rulebook-progressive-embedding.md)
- Dependencies: [029-2](029-2-checkpoint-resume.md), [029-3](029-3-lease-revision.md)

## Outcome

사용자가 queued, partial, failed, resumed, ready 상태와 progress/error/lease를 조회한다.

## Acceptance

- total/completed/remaining이 저장 상태와 일치한다.
- last error, retryability, lease owner/expiry를 반환한다.
- 기존 status/retry 계약과 OpenAPI/schema가 일치한다.

## Tests

- controller/API contract, OpenAPI, status mapping tests.
- `ui ~ entity` E2E: UI polling에서 DB checkpoint 변화 확인.

## Scope

`RuleKnowledgeController`, response DTO/schema, status mapping, API tests.
