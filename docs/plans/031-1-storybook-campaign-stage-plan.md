# 031-1 스토리북 기반 캠페인 단계 계획

- Issue: #107
- Status: ready-for-agent
- Dependencies: none

## Goal

모험을 시작하기 전에 선택된 스토리북 전체와 활성 파티를 검증하고, 출처가 추적되는 캠페인 개요와 순서 있는 단계 계획을 만든다.

## Scope

- 스토리북 resolution unit과 문서 revision을 읽어 캠페인 전체의 근거 집합을 구성한다.
- `Adventure`가 소유하는 재개 가능한 story-plan snapshot과 현재 단계 cursor를 설계·영속화한다.
- 각 단계에 장면, 목표, 갈등, 핵심 단서·NPC, 전환 조건, 근거 locator를 둔다.
- 시작 전 파티 멤버마다 활성·소유 캐릭터 시트가 있는지 확인하고, 부족한 스토리북/시트는 구체적인 준비 오류로 반환한다.
- 계획 생성은 문서 근거 없는 게임 사실을 만들지 않으며, 생성 실패·재시도에서 동일한 session revision을 중복 저장하지 않는다.

## Acceptance Criteria

- 유효한 스토리북에서 단계 순서와 단계별 근거를 포함한 계획 snapshot이 저장된다.
- 계획의 NPC·단서·장면·전환 조건마다 적어도 하나의 스토리북 근거가 연결된다.
- STORYBOOK이 없거나 활성 파티의 시트가 누락되면 세션은 시작되지 않고 어떤 준비물이 누락됐는지 응답한다.
- 재개 시 원본 문서가 바뀌어도 시작 당시의 계획 snapshot과 citation이 유지된다.

## Test Contract

- Policy unit: 출처 누락/중복/순서 오류인 단계와 누락·비소유 시트를 거절한다.
- Integration: plan snapshot, document revision, cursor의 저장·재개·idempotent start recovery를 검증한다.
- UI ↔ entity E2E: 스토리북과 세션 소유 캐릭터를 준비해 시작 준비를 완료하면 단계 계획이 생성되고, 준비물이 빠진 경우 구체적인 오류를 본다.

## Implementation Areas

- `adventure-service`: story-plan domain model, start readiness policy, repository migration/projection, session-start orchestration.
- `rulebook-service` / scenario compilation: Storybook source set와 locator를 읽는 기존 계약의 재사용.
- `character-management-service`: 활성 소유 시트 확인을 위한 읽기 계약.
- `web-ui`: 시작 준비 결과와 오류 표시.
