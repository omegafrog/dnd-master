# Product Spec: Story Plan Source Grounding

## 1. Problem and Context

Scenario-backed Adventure Story Plan 생성이 Storybook의 서술 흐름·상태·결과와 모순될 수 있다. 동시에 Planner와 GM은 난이도, 파티 구성, 분위기, 플레이 상황에 맞춰 source가 비워 둔 내용을 확장할 수 있어야 한다. 문제는 확장 자체가 아니라, 확장이 source의 명시적 사실이나 흐름을 깨거나 source 근거인 것처럼 취급될 때 발생한다.

브라우저 실행에서 관측된 `ADVENTURE_START_BLOCKED` revision mismatch는 별도 Bundle Lock 문제이며 본 작업 대상이 아니다.

## 2. Goals and Desired Outcomes

- **G-1** Scenario-backed Story Plan이 Storybook의 명시적 사실과 서사 흐름을 보존한다.
- **G-2** Planner가 source가 비워 둔 영역을 난이도·파티·플레이 상황에 맞게 비모순적으로 확장할 수 있다.
- **G-3** 확장 내용과 source 사실을 구분할 수 있다.
- **G-4** 명시적 source 모순을 생성 단계에서 차단하고 원인을 진단한다.
- **G-5** 애매한 정보는 임의로 사실 확정하지 않고 UNKNOWN/UNSPECIFIED 또는 경고로 보존한다.

## 3. Users and Actors

- **Solo Player**: source 흐름과 모순되지 않는 모험 실행의 최종 수혜자.
- **Scenario Preparation**: Story Plan을 생성·검증하는 제품 흐름.
- **Story Planner**: Storybook 제약 안에서 계획과 비모순적 확장을 제안하는 AI 역할.
- **Source Contradiction Validator**: 명시적 source 모순을 차단하는 검증 역할.
- **AI Game Master**: 검증된 계획을 runtime에서 확장·서술하는 역할.

## 4. Ubiquitous Language and Terminology

- **SOURCE_BOUND**: Storybook을 authoritative source로 사용하되, source의 빈 영역에 대한 비모순적 elaboration은 허용하는 생성 정책.
- **GENERATIVE**: Rulebook-only Bundle에서 전체 서사 골격 생성이 허용되는 정책.
- **Explicit Source Constraint**: Storybook이 명시한 인물, 상태, 위치, 사건, 순서, 원인, 결과 등 반드시 보존해야 하는 제약.
- **Compatible Elaboration**: 명시적 source 제약과 서사 흐름을 깨지 않으면서 난이도·파티·분위기·플레이 상황에 맞춰 추가하는 내용.
- **Canonical Fact**: 계획 또는 실행에서 세계의 사실로 확정되는 내용.
- **Source Contradiction**: 생성 결과가 explicit source constraint를 부정·역전·훼손하는 상태.
- **UNKNOWN / UNSPECIFIED**: source와 상황이 판단하지 못한 내용을 확정하지 않은 정상 상태.
- **Storybook Priority**: Storybook과 Rulebook이 충돌하면 Storybook의 서술·흐름을 우선하는 정책.

## 5. Core Use Cases

### UC-1 Scenario source로 Story Plan 생성

Scenario Preparation은 Storybook의 explicit constraints를 보존하면서 source가 비워 둔 부분을 compatible elaboration으로 계획한다.

### UC-2 난이도·상황 기반 비모순적 확장

Planner는 source가 허용하는 범위에서 난이도, 파티 구성, 분위기, 플레이 상황을 복합적으로 반영해 encounter, 장애물, 세부 묘사 등을 추가할 수 있다.

### UC-3 명시적 모순 차단

Validator는 entity/state/location/event/order/outcome 등 explicit source constraint와 충돌하는 후보를 거부한다.

### UC-4 애매한 정보 보존

source가 판단을 결정하지 못하면 UNKNOWN/UNSPECIFIED 또는 경고로 남긴다. 근거 부족만으로 비모순적 확장을 금지하지 않는다.

### UC-5 모순 진단 제공

실패 결과는 모순 유형, stage/claim 위치, source constraint, generated claim, source references를 제공한다.

## 6. Business Rules and Invariants

- **BR-1** Scenario-backed 생성은 SOURCE_BOUND 정책을 사용한다.
- **BR-2** Rulebook-only 생성은 GENERATIVE 정책을 사용한다.
- **BR-3** Storybook의 explicit constraint는 변경·삭제·역전할 수 없다.
- **BR-4** source에 없는 내용도 explicit constraint와 서사 흐름을 깨지 않으면 compatible elaboration으로 추가할 수 있다.
- **BR-5** compatible elaboration은 source-derived fact로 표시하지 않는다.
- **BR-6** source에 명시된 생존·사망·파괴·위치·순서·원인·결과를 반대로 바꾸는 생성은 금지한다.
- **BR-7** Storybook과 Rulebook 충돌 시 Storybook을 우선한다.
- **BR-8** Rulebook은 Storybook과 충돌하지 않는 범위에서 판정·전투·상태 적용을 제약한다.
- **BR-9** plausibility나 genre convention만으로 explicit source constraint를 덮을 수 없다.
- **BR-10** 불명확한 정보는 UNKNOWN/UNSPECIFIED 또는 경고로 표현한다.
- **BR-11** 명시적 모순은 compilation을 차단한다.
- **BR-12** 애매한 충돌은 차단하지 않고 경고·UNKNOWN으로 처리한다.
- **BR-13** GM runtime elaboration은 기존 허용 범위를 유지하며 본 변경의 구현 대상이 아니다.

## 7. States and State Transitions

Story Plan compilation outcome:

- `GENERATING`: 후보 생성 중.
- `VALIDATING`: 구조·source 흐름·규칙 정합성 검증 중.
- `READY`: 명시적 모순 없음.
- `BLOCKED`: 명시적 모순 또는 치명적 생성 실패.

전이:

- `GENERATING → VALIDATING`: parseable candidate 생성.
- `VALIDATING → READY`: explicit source contradiction 없음.
- `VALIDATING → GENERATING`: 모순 후보를 버리고 retry 정책이 허용됨.
- `VALIDATING → BLOCKED`: retry 소진 또는 치명적 모순.

## 8. Failures, Exceptions, and Boundary Conditions

- explicit source fact/state/event/order/outcome과 충돌하면 `SOURCE_CONTRADICTION`으로 차단한다.
- source가 여러 해석을 허용하고 어느 것도 explicit constraint를 깨지 않으면 compatible elaboration으로 허용한다.
- source가 판단을 결정하지 못하면 UNKNOWN/UNSPECIFIED 또는 경고를 사용한다.
- Storybook과 Rulebook이 충돌하면 Storybook 사실·흐름을 유지하고 Rulebook 충돌을 진단한다.
- source 근거가 없다는 이유만으로 모든 추가 encounter·장애물·세부 묘사를 금지하지 않는다.
- 생성 내용이 source 사실처럼 표시되면 grounding/provenance 진단 대상이다.
- 특정 Stage 번호, 고정 단계 수, 특정 브라우저 오류는 acceptance 조건이 아니다.

## 9. Inputs and Outputs

입력: Scenario Source Bundle, Storybook evidence/provenance, explicit source constraints, Rulebook constraints, party/difficulty/context settings.

출력: Story Plan, source constraint references, compatible elaboration markers, UNKNOWN/UNSPECIFIED values, warnings, compilation outcome, structured contradiction diagnostics.

## 10. Scope and Non-goals

범위: Scenario-backed Story Plan의 source-compatible elaboration, explicit constraint 보존, Storybook 우선 정책, contradiction validation, unknown handling, provenance/diagnostics, 회귀 테스트.

비범위: GM runtime Writer 구현 변경, TurnPlan, runtime memory, Narrative Verifier 전체 개편, Best-of-N, prompt optimizer, fine-tuning, Bundle Lock/revision mismatch 수정, 자동 semantic repair.

## 11. Priorities and Trade-offs

1. Storybook의 명시적 사실·흐름 보존이 창작 풍부함보다 우선.
2. 비모순적이고 유용한 elaboration은 허용.
3. 애매한 경우 추측으로 source 사실을 만들지 않고 UNKNOWN/경고를 우선.
4. Storybook과 Rulebook이 충돌하면 Storybook 우선.
5. 자동 repair보다 명확한 계약과 진단을 우선.

## 12. Success Conditions and Acceptance Criteria

- **AC-1** Scenario-backed generation이 SOURCE_BOUND 정책을 사용한다.
- **AC-2** Rulebook-only GENERATIVE 정책과 분리된다.
- **AC-3** Storybook의 명시적 entity/state/location/event/order/outcome을 뒤집는 후보가 차단된다.
- **AC-4** source에 없는 비모순적 encounter·장애물·세부 묘사는 허용된다.
- **AC-5** 추가 내용이 source-derived fact와 구분된다.
- **AC-6** 불명확한 정보가 UNKNOWN/UNSPECIFIED 또는 경고로 보존된다.
- **AC-7** Storybook과 Rulebook 충돌 시 Storybook 흐름이 유지된다.
- **AC-8** contradiction diagnostics가 유형·claim 위치·source constraint·generated claim·source refs를 제공한다.
- **AC-9** 임의의 단계 수와 난이도 설정에서 source 흐름을 깨지 않는 Story Plan이 READY가 된다.
- **AC-10** GM runtime elaboration과 Bundle Lock 오류는 본 변경 범위에 섞이지 않는다.
