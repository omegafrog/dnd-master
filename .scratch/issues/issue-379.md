---
status: planned
source_issue: https://github.com/omegafrog/dnd-master/issues/379
---

## 상태

Planned

## 의존성

[#378](https://github.com/omegafrog/dnd-master/issues/378)

## 구현 목적

상황이 끝날 때 자연스러운 같은 단계 선택 상황을 이야기 길이 허용량 안에서 먼저 고르고, 그 외에는 필수 상황 그래프에서 적격한 미사용 노드를 고른다. 선택 상황 사용량·필수 상황 진행·턴 결과를 기존 확정 경계에서 함께 반영한다.

## 범위

- `RuntimeTurnApplicationService`는 전체 모험에서 현재 단계의 자연스러운 선택 상황(허용량 잔여)과, 모든 선행 필수 노드가 `USED`이며 자신은 `USED`가 아닌 필수 상황을 후보로 제공한다. 단계 순번으로 그래프 적격성을 강제하지 않는다.
- 현재 상황이 명시적 ending이면 같은 단계 선택 후보나 필수 후보를 조회·제공하지 않고 즉시 완료를 제안한다. 해당 ending의 `USED`와 모험 완료는 기존 턴 확정에서 함께 적용한다. 명시적 ending 하나가 선택되어 `USED`로 확정되면 다른 ending 갈래는 강제하지 않는다. 미사용 적격 필수 상황이 없고 ending이 확정되지 않은 상태는 임의 상황 생성 대신 진행 거부로 처리한다.
- `RuntimeGmPromptComposer` 및 기존 생성 계약에 구체적 상황 이름·설명·핵심 요소, 사용자 행동과 확정 사건을 전달한다. 선택·계속·완료/거부를 검증한다. 다음 상황 전환에서 `newSituation` 생성은 거부해 허용량과 필수 그래프를 우회하지 못하게 한다.
- 선택 상황 허용량은 #378에서 저장된 길이와 필수 그래프 크기로 계산한다. 활성 선택 상황이 종료될 때 사용량을 한 번만 증가시킨다.
- 상황 사용·필수 진행·사용량 증가는 `PendingRuntimeState`에 보존하고 기존 `Adventure.commitRuntimeTurn` 흐름에서 게임 결과와 함께 확정한다. 굴림 대기, 저장 실패, 재시작, 중복 재개에서 미확정 상태와 재시도 안전성을 유지한다.
- 기존 `RuntimePlayerActionEvidenceAcquirer`, `RuntimeFactLookupService`, typed lookup endpoint와 근거 검색 계약을 재사용한다. 조회 담당 AI를 별도 문맥으로 한 번 호출해 이번 행동, 현재 장면, 현재·준비 상황, 게임 상태, 플레이 중 추가 사실, 잠긴 시나리오 요소, 관련 장기 기록, 허용 검색 범위, 이미 확인한 고정 근거 ID를 전달한다. 이전 대화 전체는 전달하지 않는다.
- 한 번의 혼합 자료 검색 요청으로 허용된 STORYBOOK과 RULEBOOK을 함께 dense 검색한다. BM25, 재정렬, 충분성 판단용 별도 AI 호출을 추가하지 않는다. 검색 결과는 자료 종류별 최대 5개 발췌를 조회 담당 AI에 전달한다.
- 고정 문서 근거만 종류·문서 ID·추출 버전·위치로 식별한다. 이전 확정 턴의 근거 ID를 다음 조회에서 제외하고, 버전이 바뀐 근거는 다시 조회한다. 변할 수 있는 게임 상태·추가 사실·상황·판정은 제외 목록으로 캐시하지 않는다.
- 조회 입력·출력과 검색 요청은 버전이 있는 엄격한 JSON Schema로 검증한다. 허용되지 않은 필드, 잘못된 상태·자료형·필드 수·검색 범위·출처·버전·인용 위치를 거부한다. 미발견·확인 불가는 미해결로 반환하며 성공이나 규칙 추측으로 바꾸지 않는다.
- 고정 지침과 JSON 계약을 먼저 배치해 입력 토큰 캐시 사용을 관측한다. 실제 사용량이 확인되는 제공자에서만 캐시 효과를 보고한다.
- 원문 검색 결과로 필수 상황 전체를 빠짐없이 찾았다고 주장하지 않는다. 새 서비스나 배포 경계는 만들지 않는다.

## 수용 기준

- Product Spec UC-2, R-01·03~06·08·09; Architecture Spec ST-03을 충족한다.
- 자연스러운 선택 상황이 허용량 안에 있으면 우선 선택하고, 없거나 허용량이 소진되면 적격 미사용 필수 상황을 선택한다. 적격 후보가 없고 ending이 미완료라면 새 장면으로 대체하지 않고 진행을 거부한다. 조회 한 번 → 턴 계획 → 검증·도구 실행 → 확정 순서를 지킨다.
- 다른 모험의 상태와 비공개 계획은 입력·참조·저장에 섞이지 않고 최종 플레이어 서술은 한국어이며 비공개 계획을 드러내지 않는다.
- 굴림 제출 전 상황 전환·사용을 확정하지 않는다. 정상 턴 확정 때 게임 결과와 함께 적용한다.
- 재시작·저장 실패·중복 재개 뒤 상황이 중복 확정되지 않고 후보가 중복 사용 처리되지 않는다. 기술 실패를 후보 부재나 근거 부족으로 처리하지 않는다.
- 제공 맵만 사용하고 적합한 맵이 없으면 맵 없이 진행한다. 응답은 현재 JSON 계약과 크기 한도를 따른다.

## 테스트 계약

- 단위/정책: `StoryRuntimeStateTest`, `ScenarioRuntimeTurnSafetyTest`, `SituationProposalGroundingPolicyTest`, `RuntimeGmPromptComposerTest`에서 선택·계속·완료·거부, 미사용 필수 후보와 복수 선행, ending, 길이 허용량·사용량 및 공개 범위를 검증한다. `RuntimeTurnCommitOrchestratorTest`, `RuntimeTurnPostgresIntegrationTest`에서 굴림 대기·원자적 확정·저장 실패·재시작·중복 재개와 사용량 1회 증가를 확인한다. 조회 응답 구조·출처별 최대 수량·미해결 상태를 검증한다.
- `ui ~ entity` E2E: `docs/testing/scenario-situation-candidates/runtime-situation-transition.md`를 따라 실제 UI에서 이야기 길이, 후보 선택, 후보 부재 거부, 굴림 대기 후 재개를 확인한다. 모험 시작 후 변하는 AI 흐름은 Playwright로 정적 자동화하지 않는다. 같은 세션의 `STARTING`을 10초 간격, 최대 2분 관찰하고 재요청하지 않는다.
- 성능: ST-01은 측정값·수치 목표가 없으므로 별도 기준을 추가하지 않는다.

- Plan ID: `scenario-situation-candidates-379`. 금지 사례: 단계 순번으로 그래프 후보를 제한하거나, 이미 사용한 필수 상황을 재선택하거나, ending 종료 뒤 후보를 조회·제공하거나, 길이 허용량·ending 완료를 우회하는 새 상황을 만들거나, 굴림 제출 전에 후보 사용/허용량을 저장하거나, 기술 오류를 후보 부재로 취급하거나, 비공개 계획을 노출하지 않는다. 검증 사례: 다른 단계의 적격 필수 상황 선택, 선택 상황 허용량 경계, 복수 선행 상황의 모두 사용 조건, ending 종료 시 후보 조회 미호출과 원자적 완료, 새 상황 전환 거부, 굴림 대기 후 재시작·중복 제출 및 사용량 단일 증가, 근거 ID 재조회 제외와 버전 변경 재조회.

추적 검증 기준:
- 수용: 행동에 맞는 전체 모험 후보 선택과 현재 상황의 계속 진행을 지원하며, 후보 부재 때 새 상황을 제안하지 않는다.
- 수용: 굴림 제출 전 상황 전환·사용을 확정하지 않고 턴 결과와 함께 확정한다.
- 수용: 조회 담당 AI는 허용 자료를 한 번 조회하고 응답 범위·출처·버전을 검증한다.
- 수용: 턴 호출 전에 선택 가능한 전체 후보와 각 입력 구역의 바이트 한도를 검사하며, 초과하면 후보를 자르거나 생략하지 않고 AI를 호출하지 않는다. 같은 입력은 자동 재시도하지 않는다.
- 금지: 현재 단계 순서만을 기준으로 다른 단계 후보를 거부한다.
- 금지: 굴림 대기·재시작·중복 요청에서 상황을 중복 확정하거나 비공개 계획을 노출한다.
- 금지: 입력 초과 후보를 자르거나 누락해 호출하거나 동일 입력을 자동 재시도한다.
- 확인: 다른 단계의 적격 필수 상황 선택과 후보가 남아 있는 상태에서 임의 상황 전환을 거부하는지 검증한다.
- 확인: 한 번의 혼합 자료 조회·최대 발췌 수·기존 근거 ID 제외를 검증한다.
- 확인: 활성 상황이 명시적 ending인 경우 후보 조회 함수가 호출되지 않고, 완료 제안과 USED 상태가 기존 원자적 턴 확정에 함께 반영되는지 검증한다.
- 확인: 굴림 대기 후 재시작·저장 실패·중복 재개에서 단일 확정을 검증한다.
- 확인: 8,192 byte 후보 한도 및 전체 입력 예산 사전 검사를 확인하고 로그에 모험·턴 식별자, 구역별 기대/실제 byte 수, 후보 수, 한도, 결과·실패 종류·복구 분기가 있으며 입력 원문은 없는지 검사한다.
추적 원문 ID: UC-2, UC-3, R-01, R-03, R-04, R-05, R-06, R-08, R-09, UC-1..3, INPUT-BUDGET.

## 관련 명세

- Product Spec: [docs/specs/scenario-situation-candidates/product-spec.md](https://github.com/omegafrog/dnd-master/blob/spec/combat-movement-spell-range/docs/specs/scenario-situation-candidates/product-spec.md)
- Architecture Spec: [docs/specs/scenario-situation-candidates/architecture-spec.md](https://github.com/omegafrog/dnd-master/blob/spec/combat-movement-spell-range/docs/specs/scenario-situation-candidates/architecture-spec.md)
- Architecture requirements: [ST-03..05](https://github.com/omegafrog/dnd-master/blob/spec/combat-movement-spell-range/docs/specs/scenario-situation-candidates/architecture-spec.md#ST-03-05)

## 다이어그램

- Product: [UC-2 후보 선택·생성](https://github.com/omegafrog/dnd-master/blob/spec/combat-movement-spell-range/docs/specs/scenario-situation-candidates/diagrams/product/UC-2.activity.svg)
- Architecture: [모험별 상황 저장 관계](https://github.com/omegafrog/dnd-master/blob/spec/combat-movement-spell-range/docs/specs/scenario-situation-candidates/diagrams/architecture/situations.class.svg)
- Architecture: [턴 확정 상태 전이](https://github.com/omegafrog/dnd-master/blob/spec/combat-movement-spell-range/docs/specs/scenario-situation-candidates/diagrams/architecture/turn-commit.state.svg)

## 추적용 수용 문장

- 상황 종료 후 자연스러운 같은 단계 선택 후보가 남은 허용량 내에 있을 때 우선 선택하고, 그 외에는 단계와 무관하게 모든 선행 노드가 USED이며 본인은 미사용인 필수 상황만 선택한다.
- 현재 상황이 명시적 ending이면 후보 조회 없이 완료를 제안하고, 같은 기존 턴 확정에서 ending을 USED 처리하면서 모험을 완료한다. 다른 끝 갈래는 모두 방문하지 않는다. 미완료 ending인데 적격 필수 후보가 없으면 임의 상황 생성 대신 진행을 거부한다.
- 선택 상황 사용량은 끝난 선택 상황당 원자적 턴 확정에서 한 번만 증가하며 굴림 대기·실패·재시작·중복 재개에서 조기/중복 반영되지 않는다.
- 다음 상황 전환의 새 장면 생성은 길이 허용량이나 그래프 순서를 우회하지 못하도록 거부한다.
- 다른 단계의 적격 필수 상황, 복수 선행 노드 전부 USED 조건, 이미 사용한 노드 재선택 거부, 선택 상황 허용량 경계, ending 종료 시 후보 조회 없음·원자적 완료·alternate ending 비강제, 새 장면 전환 거부를 검증한다.
- 굴림 대기 후 재시작·저장 실패·중복 재개에서 단일 확정과 선택 상황 사용량의 단일 증가를 검증한다.
