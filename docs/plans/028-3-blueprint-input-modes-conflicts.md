# 028-3 - Blueprint 입력 모드와 충돌 검토

- Status: completed
- Issue: [#92](https://github.com/omegafrog/dnd-master/issues/92)
- Parent: 028
- Dependencies: 028-1

## Outcome

캐릭터 필드와 태그를 `MANUAL_INPUT`, `SELECT_OPTION`, `FIXED_VALUE`로 구분하고, 누락·충돌·사용자 보완을 versioned Blueprint revision에 저장한다.

## Scope

- `CharacterCreationBlueprintCompiler` 입력 모드 분류와 manual fallback.
- 문서 유형 간 값/선택지 충돌 감지 및 `CONFLICT_REVIEW` 진단.
- fixed value 자동 입력과 사용자 수정 허용.
- 사용자 field/tag/option 추가·수정 API와 revision/optimistic locking.
- 개발용 Evidence/confidence/status view model 유지.

## Acceptance

- 근거 없는 필드만 manual input으로 남고 다른 추출 결과는 유지된다.
- 고정값은 자동 채워지지만 사용자가 수정할 수 있다.
- 충돌은 자동 병합되지 않고 검토 상태로 저장된다.
- 수동 태그·선택지·값이 다음 revision에 보존된다.
- unresolved conflict/manual requirement는 publish를 막는다.

## Tests

- compiler state, conflict, fallback, revision mutation, publish guard unit/API tests.
- `ui ~ entity` E2E: Blueprint review → 값/태그/선택지 수정 → revision 확인.

## Implementation scope

`adventure-service` blueprint domain/compiler/application/API/persistence, `web-ui` review state and dynamic input tree tests.
