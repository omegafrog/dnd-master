# 027-3 - Blueprint 기반 캐릭터 생성 연결 및 Playwright

- Status: ready-for-agent
- Issue: [#88](https://github.com/omegafrog/dnd-master/issues/88)
- Parent: [#85](https://github.com/omegafrog/dnd-master/issues/85)
- Dependencies: [027-2](027-2-dynamic-blueprint-review-ui.md)

## Outcome

게시된 동적 Blueprint 값을 실제 `character-management-service` 캐릭터 시트 생성에 사용한다.

## Scope

- `CharacterCreationPage.tsx` tree value serialization.
- `SetupApi.ts` character creation payload.
- nested `blueprintValues` → starting abilities/structured fields mapping.
- character-management-service validation/persistence contract.
- publication gating, revision checks, final UI result state.

## Acceptance

- 동적 root/child 값이 character sheet payload로 정확히 변환된다.
- `starting_ability_scores.*` 값이 보존된다.
- 게시되지 않은 Blueprint로는 생성할 수 없다.
- 생성 결과가 UI에 표시된다.

## Tests

- character creation API contract/persistence tests.
- nested serialization tests.
- `CharacterCreationPage` tests.
- `ui ~ entity` Playwright: document selection → Agent extraction → review → publish → character sheet creation.

## Implementation scope

`web-ui` `CharacterCreationPage.tsx`, `SetupApi.ts`, related tests; `character-management-service` controller/API tests; system/e2e fixtures and artifacts.
