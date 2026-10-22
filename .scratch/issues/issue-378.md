---
status: in-progress
source_issue: https://github.com/omegafrog/dnd-master/issues/378
---

## 상태

Planned

## 의존성

없음

## 구현 목적

추가 룰북의 필수 상황과 그 사이의 중간 상황 후보를 새 모험마다 독립 준비하고, 준비 때 고른 이야기 길이와 원문에 명시된 필수 상황 그래프의 결말 표시를 저장·복원한다.

## 범위

- 기존 `StageArtifactPreparationApplicationService.prepare`와 실제 생성 연결을 확장해 기존 근거 자료와 스토리 모델을 제공하고, 구체적인 이름·장면 설명·핵심 요소·원문 추출/GM 보완 출처를 생성·검증·저장한다.
- `PreparationFlow`에서 짧게/보통/길게 이야기 길이를 선택하고 기존 세션 생성 요청에 전달한다. 서버 세션 저장·복원 및 실제 모험 초기 상태까지 전달한다. 허용량 숫자는 UI에 표시하지 않는다.
- 원문에 명시된 선행 관계와 `isEnding` 표식을 보존한다. 시작 root 하나, 명시 결말 하나 이상, 모든 비결말 노드에서 결말까지의 도달성, 결말 노드 후속 없음, 비순환을 검증한다. 복수 선행과 합류를 허용한다.
- 준비 목록의 순서로 추가 관계를 만들지 않고 후보 수의 최솟값도 요구하지 않는다. 이야기 연결에 중간 후보가 필요한 경우에는 그 연결을 돕는 구체 후보를 준비한다. 후보가 실제로 필요하지 않은 경우에는 0개도 허용한다.
- 기존 `SituationDefinition`, 준비 저장소, 모험 소유 키를 사용한다. 새 테이블·서비스를 만들지 않으며 준비 세트의 기존 게시 경계를 유지한다.
- 게시 전과 실제 시작 경계에서 opening의 미완료 필수 선행 상황을 거부한다. 일부 자료만 게시하거나 시작 상태를 만들지 않는다.
- 선택된 추가 룰북이 있는 새 모험에 한정하며 다른 모험의 상황을 공유하지 않는다. 같은 원본 지도 격자의 재사용·수정 선택은 #393이 다룬다.

## 수용 기준

- Product Spec UC-1, R-01~03, R-07, R-09; Architecture Spec ST-03·ST-05를 충족한다.
- UI에 세 단계 이야기 길이만 보이고 정확한 선택 상황 개수는 보이지 않으며, 서버 세션 저장·재조회에서 선택값이 보존된다.
- 선택 상황 허용량은 필수 그래프 노드 수에 짧게 0.25, 보통 0.5, 길게 1.0을 곱한 뒤 올림한다. 필수 노드 5개 fixture에서 결과는 각각 2·3·5이며, 계산 결과 숫자는 UI에 보이지 않는다.
- 명시적 결말 표시는 추출 응답부터 `StageBackbone` 저장·복원까지 보존되고, 끝점의 후속 없음만으로 결말을 추론하지 않는다.
- 원문에서 확인된 필수 상황과 명시 선행 관계를 보존하고, 명시되지 않은 순서를 만들지 않는다. 준비 모델은 원문 발췌와 인접 필수 상황의 요약·설명을 비교해 장면 사이에 서술상 빠진 이동·시간 경과·사건 연결이 있는지 판단한다. 빠진 연결이 명시된 사례에서는 그 연결만 잇는 구체 후보를 만들고, 두 상황이 직접 인과 또는 시간상 이어지는 사례에서는 0개를 허용한다. 이 판단은 후보 최소 개수 규칙으로 바꾸지 않는다.
- 검증 fixture는 두 인접 필수 상황 사이에 원문이 명시한 이틀 이동이 있지만 그 이동을 묘사하는 상황이 없는 경우를 연결 필요 사례로 사용한다. 후보 하나가 이 이동을 묘사하고 새 갈등을 추가하지 않으면 통과다. 같은 장면의 문을 열고 안에 있는 인물을 발견하는 직접 연속 사례는 0개 후보로 통과해야 한다.
- 같은 시나리오를 쓰는 두 신규 모험은 각자 독립된 상황 세트를 저장·복원하며, 재시도는 같은 모험 ID로 게시 경계를 따른다.
- 원문 근거 부족 보완은 출처를 구분한다. 실제 생성·검증·저장 실패는 근거 부족과 구분하고 부분 세트 게시를 막는다.
- 미완료 필수 선행 상황이 있는 opening은 게시와 실제 시작에서 모두 거부되며 시작 상태가 생성되지 않는다.

## 테스트 계약

- 단위/정책: `StageArtifactPreparationApplicationServiceTest`, `StageArtifactPolicyTest`, `ScenarioPackageStageArtifactAdapterTest`, `AdventureSessionStageStartTest`에서 그래프 구조·명시 결말·출처·모험 소유권·길이 입력 검증과 저장·복원을 확인한다. `PostgresStageArtifactRepositoryIntegrationTest`에서 원자적 게시와 복원을 확인한다.
- `ui ~ entity` E2E: `docs/testing/scenario-situation-candidates/runtime-situation-transition.md`의 준비 절차를 실제 UI에서 읽으며 실행한다. 브라우저 기반 AI 진행을 Playwright로 자동화하지 않는다. `STARTING`이면 같은 세션을 10초 간격, 최대 2분 관찰한다. 테스트 중 데이터 초기화나 새 룰북 추출은 하지 않는다.
- 성능: ST-01은 기준 측정·수치 목표가 없으므로 별도 성능 기준을 추가하지 않는다.

- Plan ID: `scenario-situation-candidates-378`. 금지 사례: 명시되지 않은 순서나 결말을 만들거나, 후보가 적다는 이유로 실패하거나, 다른 모험 자료를 공유하거나, 기술 실패를 근거 부족 보완으로 숨기지 않는다. 검증 사례: 두 새 모험 독립 저장·복원, 세 길이 값 전달·보존 및 잘못된 값 거부, 원문 발췌와 인접 필수 상황에 이동·시간 경과가 명시된 fixture에서는 그 연결 후보 생성, 두 상황의 직접 인과가 명시된 fixture에서는 후보 0개 수용, 합류·결말 그래프 검증, 미완료 선행 opening 게시·시작 거부, 같은 모험 재시도, 저장 원자성.

추적 검증 기준:
- 수용: 원문에서 확인된 필수 상황과 명시 선행 관계를 보존하고, 명시되지 않은 순서를 만들지 않는다.
- 수용: 이야기 연결에 중간 후보가 필요한 경우 구체 후보가 준비된다. 필요하지 않은 경우에는 후보가 0개 또는 1개여도 후보 수만으로 실패하지 않는다.
- 수용: opening의 미완료 필수 선행 상황은 게시·시작에서 거부된다.
- 수용: 선택 가능한 전체 후보를 턴 문맥 JSON으로 직렬화한 크기는 8,192 UTF-8 byte 이하다. 초과하면 기존 응답을 한 차례만 보정 입력으로 재사용하고 필수 상황은 보존하며, 재초과 시 전체 게시를 막는다.
- 금지: 후보 개수가 적다는 이유만으로 준비가 실패한다.
- 금지: 다른 모험의 상황을 재사용하거나 기술 실패를 근거 부족 보완으로 숨긴다.
- 금지: 보정 과정에서 STORYBOOK을 다시 조회하거나 필수 상황을 누락한다.
- 확인: 필수 노드 5개에서 세 길이의 허용량이 2·3·5인지, 필수 노드가 1개인 경계에서 세 길이 결과가 모두 1인지 확인한다. 화면에는 길이 선택지만 보이고 계산 숫자는 보이지 않는다.
- 확인: 두 신규 모험의 준비 자료를 독립 저장·복원한다.
- 확인: 이틀 이동이 명시된 두 필수 상황 fixture에서는 후보가 이동을 묘사하고 새 갈등을 추가하지 않는지 확인한다. 같은 장면에서 문을 열고 인물을 발견하는 직접 연속 fixture는 후보 0개를 허용하는지 확인한다.
- 확인: 재시도·저장 실패 뒤 부분 준비 자료가 게시되지 않는지 확인한다.
- 확인: 8,192 byte 경계와 초과·한 차례 보정·재초과 게시 거부를 검사하고 진단에 시도 번호, 기대/실제 byte 수·후보 수·복구 분기·실패 종류가 남는지 확인한다.
추적 원문 ID: UC-1, R-01, R-02, R-03, R-07, R-09, UC-1..3, INPUT-BUDGET.

## 관련 명세

- Product Spec: [docs/specs/scenario-situation-candidates/product-spec.md](https://github.com/omegafrog/dnd-master/blob/spec/combat-movement-spell-range/docs/specs/scenario-situation-candidates/product-spec.md)
- Architecture Spec: [docs/specs/scenario-situation-candidates/architecture-spec.md](https://github.com/omegafrog/dnd-master/blob/spec/combat-movement-spell-range/docs/specs/scenario-situation-candidates/architecture-spec.md)
- Architecture requirements: [ST-03..05](https://github.com/omegafrog/dnd-master/blob/spec/combat-movement-spell-range/docs/specs/scenario-situation-candidates/architecture-spec.md#ST-03-05)

## 다이어그램

- Product: [UC-1 준비 절차](https://github.com/omegafrog/dnd-master/blob/spec/combat-movement-spell-range/docs/specs/scenario-situation-candidates/diagrams/product/UC-1.activity.svg)
- Product: [UC-1 초기 상황 준비](https://github.com/omegafrog/dnd-master/blob/spec/combat-movement-spell-range/docs/specs/scenario-situation-candidates/diagrams/product/UC-1.usecase.svg)
- Architecture: [모험별 상황 저장 관계](https://github.com/omegafrog/dnd-master/blob/spec/combat-movement-spell-range/docs/specs/scenario-situation-candidates/diagrams/architecture/situations.class.svg)

## 추적용 수용 문장

- 준비 화면의 세 단계 길이 선택은 정확한 선택 상황 개수를 숨기고, 허용값 검증 뒤 세션·초기 모험 상태로 전달·복원된다.
- 명시적 결말 표식은 추출·모험별 저장·복원 과정에 유지되며 결말을 후속 간선 부재만으로 추론하지 않는다.
- 필수 상황은 시작 하나, 명시 결말 하나 이상, 비결말 결말 도달 가능성, 결말 후속 없음, 비순환성을 지키며 다중 선행/합류를 허용한다.
