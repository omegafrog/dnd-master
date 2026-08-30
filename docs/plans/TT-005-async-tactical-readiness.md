# TT-005: Durable Async Tactical Readiness Workflow

- Issue: [#242](https://github.com/omegafrog/dnd-master/issues/242)
- Status: `completed`
- Dependencies: 없음
- Parent: [#237](https://github.com/omegafrog/dnd-master/issues/237)

## 구현 목적

Stage 진입 시 tactical preparation을 durable async job으로 enqueue한다. `READY` 전 activation은 structured readiness conflict를 반환하고 request thread에서 generation을 실행하지 않는다.

## Implementation Scope

- lease token/expiry를 가진 tactical preparation job claim
- `TacticalScenePreparationWorker`와 unfinished-job recovery
- stage-entry create-or-get enqueue
- `TACTICAL_SCENE_NOT_READY` 409 response contract
- activation guard와 retry contract

## Acceptance Criteria

- StoryPlan `READY`가 Tactical `READY`를 의미하지 않음
- stage entry request가 generation 완료를 기다리지 않음
- restart 후 unfinished job 회수 가능
- READY 전 structured 409, READY 후 activation 성공

## Test Contract

- Policy unit: current/future/absent/failed readiness와 lease ownership
- Persistence integration: claim expiry, duplicate worker, restart recovery
- UI/API ~ entity E2E: stage entry → structured 409 → worker READY → map activate

## Implementation Boundaries

- Progress phase/count UI는 TT-006
- raw job failure reason player projection 비노출
- dirty `potent-brew-browser.spec.ts` timeout 변경 보존

## Completion Evidence

- Player preparation path now enqueues and reads readiness; durable worker owns generation.
- Added worker polling seam, restart queue recovery, lease-token/expiry migration, and structured `TACTICAL_SCENE_NOT_READY` 409 response.
- UI now polls asynchronous tactical readiness and activates maps only after `READY`.
- Verification: targeted tactical tests and `npm --prefix src/web-ui run typecheck` passed. PostgreSQL Testcontainers test could not start because Docker is unavailable.
