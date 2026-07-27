# 021-1 - 모험 시작·파티 고정·런타임 바인딩 전환

- Status: completed
- Dependencies: 020
- Supersedes: remaining scope of 021

## Outcome

사용자 명시적 start로 draft Adventure Session을 시작하고, 시작 뒤 파티·제어 방식을 고정한다. 생성된 Adventure와 Runtime Binding은 시작 시점의 Scenario Package revision과 session party 참조를 사용한다.

## Current Baseline

- 020이 draft session, party CRUD, character limit, optimistic version, control mode와 여섯 초기 속성 변경 정책을 저장한다.
- Character Management는 session policy seam을 가지지만 production wiring은 항상 draft 정책을 반환하고 이름·레벨만 검증한다.
- Adventure와 Runtime Binding은 여전히 단일 `characterSheetId`를 소유하며 start endpoint와 started 상태가 없다.

## Scope

- `AdventureSession`의 `DRAFT → STARTED` 전이, started Adventure/runtime binding link, start idempotency key와 version 조건부 저장.
- start 시 compiled package, 고정 package revision, party 최소 1명, control mode, character limit 재검증.
- started session의 party add/replace/remove 및 control mode 변경 거절.
- `Adventure`·`RuntimeBinding` 단일 `characterSheetId` 모델·schema·API를 session party 참조로 전환.
- start 뒤 package switch를 차단하거나 start lifecycle로 통합해 runtime revision을 고정.
- Character Management production session-policy adapter와 여섯 초기 속성 policy 전체 검증.
- start API와 UI CTA, started 상태의 party editing 비활성화.
- legacy direct Adventure creation이 start/freeze를 우회하지 않도록 제거·차단·session start 경유 중 하나로 정리.

## Exclusions

- DIRECT/AGENT 실제 턴 분기·자동 실행은 022.
- 종료 session sheet 삭제 outbox는 023.

## Acceptance Criteria

- 유효한 draft party만 한 번 start할 수 있다.
- 재시도는 같은 결과를 반환하고, 동시 start는 한 번만 성공한다.
- start 뒤 party 소속과 control mode 변경은 거절된다.
- runtime은 start 시점 package revision과 session party 참조를 사용한다.
- production Character Management 조회가 여섯 초기 속성 변경 정책을 모두 반영한다.
- UI는 party 작성 → start → started party 편집 불가 상태를 표시한다.

## Test Contract

- Policy unit: start 조건, 상태 전이, party freeze, idempotency.
- Persistence/API integration: session version 충돌, start 재시도, Adventure/Runtime Binding party 참조 영속.
- Character integration: started session policy와 여섯 초기 속성 flag 검증.
- UI~entity E2E: party 작성 → start → party edit 거절 → runtime binding 확인.

## Implementation Scope

- `adventure-service`: session domain/application/API/persistence, Adventure·Runtime Binding migration, legacy path 정리, tests.
- `character-management-service`: session-policy read adapter, full initial-attribute policy enforcement, tests.
- `web-ui`: session party start CTA, started state, runtime binding API types, E2E.
