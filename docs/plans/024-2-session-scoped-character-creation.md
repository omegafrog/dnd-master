# 024-2 - 세션 snapshot·session-scoped character creation

- Status: completed
- Issue: [#65](https://github.com/omegafrog/dnd-master/issues/65)
- Dependencies: 024-1
- Parent: [024](024-character-creation-flow.md)

## Outcome

게시된 Package/Blueprint revision으로 Adventure Session 초안을 만들고, 실제 session ID에 캐릭터 시트를 귀속한다.

## Acceptance criteria

- Session create request가 `blueprintId + blueprintRevision`을 받는다.
- Session이 Package/Blueprint revision을 snapshot한다.
- 게시되지 않은 Blueprint 또는 revision mismatch를 거절한다.
- Character Sheet에 `session_id`를 추가한다.
- API는 `POST /internal/v1/adventure-sessions/{sessionId}/character-sheets`를 사용한다.
- session ID 누락 시 거절하고 UUID fallback을 생성하지 않는다.
- 세션 서비스가 DRAFT 상태와 Blueprint revision을 검증한다.
- Character Management policy 조회가 session ID를 사용한다.
- 기존 `adventure_id` 런타임 read/update 호환을 유지한다.

## Test contract

- Policy unit: snapshot immutability and DRAFT/revision rules.
- Integration: session persistence, session_id migration, character persistence.
- API contract: missing/invalid session and no-random-UUID regression.
- `ui ~ entity` E2E: published Blueprint → session draft → session-scoped sheet creation.

## Implementation scope

Adventure Session create/domain/repository/controller, Character Sheet model/application/controller/repository, Flyway migrations, session policy adapter, compatibility tests, API client seams.
