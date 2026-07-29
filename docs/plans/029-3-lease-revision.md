# 029-3 - Lease·Revision Guard

- Status: completed
- Issue: [#96](https://github.com/omegafrog/dnd-master/issues/96)
- Parent: [029](029-rulebook-progressive-embedding.md)
- Dependencies: [029-1](029-1-progressive-embedding.md), [029-2](029-2-checkpoint-resume.md)

## Outcome

동시 worker, lease 만료, 늦은 응답, source revision 변경이 index를 오염시키지 않는다.

## Acceptance

- 하나의 live lease만 존재한다.
- lease token/owner/revision 불일치 write는 거부된다.
- lease 만료 후 새 worker가 checkpoint를 reclaim한다.
- stale worker 결과와 이전 revision 결과가 저장되지 않는다.

## Tests

- policy/repository unit: claim, renew, expiry, CAS rejection.
- `ui ~ entity` E2E: 두 worker/API 흐름에서 Postgres stale write 거부 확인.

## Scope

lease claim/renew/reclaim, conditional SQL, revision guard, concurrency tests.
