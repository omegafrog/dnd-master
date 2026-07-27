# 023 - 세션 종료 캐릭터 시트 정리

- Status: completed
- Dependencies: 020, 021
- Issue: [#55](https://github.com/omegafrog/dnd-master/issues/55)

## Outcome

완료 또는 삭제된 세션의 귀속 캐릭터 시트를 아웃박스 이벤트로 제거하고, 실패 시 재시도한다.

## Scope

- 세션 완료·삭제 도메인 동작과 `CharacterSheetsDeletionRequested` 아웃박스
- Character Management 삭제 소비, 멱등성, 재시도
- 종료 세션 시트 읽기·수정 차단
- 삭제 대기·실패·재시도 관측

## Acceptance Criteria

- 종료 세션 시트는 삭제 완료 전에도 사용·변경할 수 없다.
- 삭제 요청은 실패해도 재시도한다.
- 반복 이벤트는 같은 시트를 안전하게 한 번만 정리한다.
- 성공 뒤 세션 소속 시트가 남지 않는다.

## Test Contract

- Policy unit: 종료 이벤트, 접근 차단, 멱등 삭제.
- Integration: 아웃박스 영속과 소비 재시도.
- UI~entity E2E: 세션 삭제 → 시트 접근 불가.

## Implementation Scope

- `adventure-service` 종료 lifecycle, outbox persistence/worker, observability, tests.
- `character-management-service` deletion consumer, access guard, migration, tests.
- `web-ui` 종료·삭제 상태와 시트 비활성화/제거 표시, tests.
