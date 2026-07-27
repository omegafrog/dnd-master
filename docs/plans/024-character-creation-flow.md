# 024 - 세션 기반 캐릭터 생성 흐름

- Status: approved
- Dependencies: 024-1, 024-2, 024-3, 024-4
- Product Spec: `docs/specs/product-spec.md`
- Architecture Spec: `docs/specs/architecture-spec.md`

## Outcome

문서에서 생성한 게시 Blueprint를 선택해 세션 초안을 만들고, 실제 session ID에 귀속된 캐릭터를 생성한 뒤 파티에 추가하고 모험을 시작한다.

## Slices

- 024-1: Blueprint 컴파일·게시
- 024-2: 세션 snapshot·session-scoped character creation
- 024-3: 파티 귀속 검증·모험 시작
- 024-4: Web UI 전체 흐름

## Dependency

`024-1 → 024-2 → 024-3 → 024-4`

## Overall acceptance

- 세션 없는 캐릭터 생성 요청 불가.
- Blueprint revision과 session ID가 전체 흐름에서 유지된다.
- 핸드아웃 우선, 룰북 fallback, 충돌 검토, 누락 필드 수동 입력이 동작한다.
- 유효한 session-owned sheet만 파티에 추가된다.
- 파티 준비 완료 후 모험 시작 가능.

## Overall test contract

- 각 slice의 policy unit/integration/API contract 테스트.
- 전체 `ui ~ entity` Playwright E2E.

## Implementation scope

`adventure-service`, `character-management-service`, `web-ui`, module Flyway migrations, cross-context ports/adapters, tests.
