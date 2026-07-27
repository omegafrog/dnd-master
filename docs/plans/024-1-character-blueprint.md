# 024-1 - Blueprint 컴파일·게시

- Status: approved
- Dependencies: none
- Parent: [024](024-character-creation-flow.md)

## Outcome

Scenario Package 컴파일이 versioned CharacterCreationBlueprint를 생성하고, 검토 가능한 상태로 저장·게시한다.

## Acceptance criteria

- 핸드아웃 여러 개를 하나의 템플릿으로 병합한다.
- 핸드아웃 필드가 룰북 필드보다 우선한다.
- 핸드아웃 충돌은 자동 해결하지 않고 `NEEDS_REVIEW`로 표시한다.
- 핸드아웃이 없으면 룰북 기반 Blueprint를 생성한다.
- 추출 실패 기존 필드는 `MANUAL_INPUT_REQUIRED`로 표시한다.
- MVP 커스텀 필드는 거절한다.
- Blueprint 생성 실패는 전체 Package 컴파일 `FAILED`다.
- 게시 전 field/options/constraints/evidence/diagnostics/revision을 API에서 읽을 수 있다.

## Test contract

- Policy unit: merge priority, conflict, missing field, custom field rejection, compile failure.
- Integration: Blueprint/package persistence and immutable revision.
- API contract: preparation response and review/publish diagnostics.
- `ui ~ entity` E2E: document compile → Blueprint status/evidence review.

## Implementation scope

`ScenarioPackageCompilationService`, Scenario Preparation domain/application/API, Blueprint repository and Flyway migration, relevant adapters, persistence/API/UI preparation tests.
