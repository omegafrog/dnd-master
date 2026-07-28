# 027 - Agent 기반 캐릭터 Blueprint 추출 및 생성 UI

- Status: approved
- Issue: [#85](https://github.com/omegafrog/dnd-master/issues/85)
- Product Spec: `docs/specs/product-spec.md`
- Architecture Spec: `docs/specs/architecture-spec.md`
- Slices: [027-1](027-1-agent-character-tag-extraction.md), [027-2](027-2-dynamic-blueprint-review-ui.md), [027-3](027-3-character-creation-agent-e2e.md)

## Outcome

RULEBOOK/STORYBOOK 원문을 Agent가 분석해 동적 캐릭터 입력 태그를 추출한다. Blueprint 검토 UI를 거쳐 게시된 값을 실제 캐릭터 시트 생성에 사용한다.

## Acceptance

- 고정 6개 필드 정규식 추출을 제거한다.
- Agent가 새 root/child 태그, 입력 모드, 선택지, 제안값, 근거, confidence를 반환한다.
- STORYBOOK 정의가 RULEBOOK 정의보다 우선한다.
- 문서에 없는 값은 생성하지 않는다.
- 사용자는 추출 결과를 검토·보완·게시할 수 있다.
- 게시된 Blueprint 값으로 캐릭터 시트가 생성된다.
- UI가 동적 tree, 입력 모드, 근거, 충돌, confidence를 표시한다.

## Test contract

- Agent contract/schema, compiler, persistence, API unit tests.
- 모든 slice에 `ui ~ entity` Playwright 검증 포함.
- 최종 E2E: 문서 선택 → Agent 추출 → 검토 → 게시 → 캐릭터 생성.

## Dependency

`027-1 → 027-2 → 027-3`
