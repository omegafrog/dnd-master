# 024-3 - 파티 귀속 검증·모험 시작

- Status: approved
- Dependencies: 024-2
- Parent: [024](024-character-creation-flow.md)

## Outcome

생성된 시트가 현재 세션에 실제 귀속된 경우에만 파티에 추가되고, 검증된 파티만 런타임을 시작한다.

## Acceptance criteria

- 파티 추가 전 Character Management port를 호출한다.
- nonexistent, wrong-session, wrong-owner sheet를 거절한다.
- 검증 실패 시 Party Aggregate를 저장하지 않는다.
- 유효한 시트만 control mode와 초기 변경 정책을 가진 Party member가 된다.
- 시작 시 Package/Blueprint revision 일치를 검증한다.
- 최소 1명·모든 control mode 선택 시에만 시작한다.
- 시작 후 party add/replace/remove/control mode 변경을 거절한다.
- 캐릭터 생성 성공 후 파티 추가 실패를 미연결 시트로 재시도·정리 가능하게 한다.

## Test contract

- Policy unit: party validation, limit, start freeze.
- Integration: cross-context ownership/session validation.
- API contract: party add and start revision/error responses.
- `ui ~ entity` E2E: valid sheet → party add → control mode → adventure start.

## Implementation scope

Adventure Session application/domain, Character Management existence/ownership query port and HTTP adapter, start validation, outbox/cleanup compatibility, integration and E2E tests.
