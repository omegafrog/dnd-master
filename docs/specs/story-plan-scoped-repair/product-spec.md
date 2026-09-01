# Product Spec: Story Plan 최소 범위 Repair

## 1. Problem and Context

Adventure Story Plan 생성 후보가 validation에 실패하면 현재 전체 계획을 다시 생성할 수 있다. 이 과정에서 이미 검증을 통과한 영역이 함께 바뀌어 새로운 citation, source fact, combat participant 오류가 생기고, 재생성 budget이 소진되어 계획 게시가 막힌다. 실제 오류 원인은 전달되지만 수정 범위가 과도해 실패가 연쇄되는 것이 문제다.

## 2. Goals and Desired Outcomes

- **G-1** validation 실패 시 오류가 가리키는 최소 Story Plan 영역만 수정한다.
- **G-2** 현재 validation을 통과한 영역은 다른 오류를 고치는 동안 보존한다.
- **G-3** repair가 불가능한 경우에만 전체 Story Plan 재생성을 허용한다.
- **G-4** repair와 전체 재생성의 시도 및 실패 이유를 추적 가능하게 한다.
- **G-5** 불완전하거나 검증되지 않은 계획을 게시하지 않는다.

## 3. Users and Actors

- **Solo Player**: 준비 가능한 검증된 모험 계획을 기대하는 사용자.
- **Scenario Preparation**: Story Plan 후보를 생성·검증·게시하는 흐름.
- **Story Planner**: 계획 후보를 생성하거나 제한된 범위로 수정하는 AI 역할.
- **Story Plan Validator**: 구조, 규칙, source fact, citation, combat participant 정합성을 검증하는 역할.
- **Repair Policy**: violation을 repair 가능 또는 전체 재생성 필요로 분류하는 정책 역할.
- **Repair Scope Resolver**: 각 violation에 대해 수정 허용 범위를 결정하는 역할.

## 4. Ubiquitous Language and Terminology

- **Validation Violation**: Story Plan 후보가 계약을 위반했다는 구조화된 진단.
- **Repairable**: 기존 후보를 유지하면서 특정 범위만 수정할 수 있는 violation.
- **Regeneration Required**: 최소 범위 repair로 안전하게 복구할 수 없어 전체 후보 재생성이 필요한 violation.
- **Repair Scope**: provider가 수정할 수 있고 결과에 반영할 수 있는 Story Plan의 최소 영역.
- **Previous Full Candidate**: 현재 validation을 통과한 값과 실패 영역을 모두 포함한 직전 후보.
- **Scoped Merge**: repair 결과 중 허용된 Repair Scope만 기존 후보에 반영하는 병합.
- **Canonical Source Fact**: Story Plan이 새로 발명하지 않고 참조해야 하는 정본 source fact 집합.
- **Repair Attempt**: 기존 후보와 구조화된 violation, Repair Scope를 이용한 제한 수정 시도.
- **Full Regeneration**: 기존 후보를 대체할 전체 Story Plan 생성 시도.

## 5. Core Use Cases

### UC-1 최초 Story Plan 생성 및 검증

Scenario Preparation은 Story Plan을 생성하고 validator 결과를 받는다. 유효하면 게시 흐름으로 진행한다.

### UC-2 최소 범위 repair

Repair Policy가 violation을 Repairable로 분류하면 시스템은 Previous Full Candidate, 구조화된 violation, deterministic Repair Scope, 사용 가능한 canonical source facts를 Story Planner에 전달한다. 결과는 Scoped Merge 후 재검증한다.

### UC-3 여러 violation의 제한 병합

동시에 여러 violation이 있으면 Repair Scope를 합집합으로 계산한다. 각 scope 밖의 기존 값은 유지한다.

### UC-4 범위를 알 수 없는 실패의 전체 재생성

Repair Scope를 계산할 수 없거나 repair 결과를 안전하게 병합할 수 없으면 기존 후보를 보존한 채 전체 재생성을 최대 1회 시도한다. 재생성 실패 시 준비를 차단한다.

### UC-5 검증 실패 원인 추적

각 시도는 INITIAL_GENERATION, REPAIR, FULL_REGENERATION 유형과 violation, scope, 결과를 기록한다. 실패 시 사용자에게 준비 실패와 구체적인 validation 원인을 제공한다.

## 6. Business Rules and Invariants

- **BR-1** 현재 validation을 통과한 Story Plan 영역은 다른 violation을 수정하는 과정에서 변경하지 않는다.
- **BR-2** Repairable violation은 full regeneration보다 repair를 우선한다.
- **BR-3** `MISSING_RULE_CHECK`, `MISSING_RULE_OUTCOME`, `SOURCE_FACT_CLAIM_UNKNOWN_CITATION`, `COMBAT_PARTICIPANT_SOURCE_UNSUPPORTED`는 Repairable 후보로 분류한다. 단, 안전한 scope 계산이 불가능하면 BR-7을 적용한다.
- **BR-4** Repair Scope 밖의 provider 반환 변경은 Scoped Merge에서 폐기한다.
- **BR-5** repair 요청은 Previous Full Candidate와 구조화된 violation 및 deterministic Repair Scope를 포함한다.
- **BR-6** provider는 scope 밖 내용을 변경하거나 새로운 citation/source fact를 임의로 발명하지 않는다.
- **BR-7** Repair Scope를 계산할 수 없거나 scoped merge가 불가능하면 Full Regeneration을 최대 1회만 허용한다.
- **BR-8** Full Regeneration 이후에도 validation에 실패하면 더 이상 전체 후보를 반복 생성하지 않는다.
- **BR-9** validation 실패 중 기존 정상 영역과 candidate는 보존하며 부분적으로 유효한 계획을 게시하지 않는다.
- **BR-10** violation은 재시도 요청에서 누적·추적 가능해야 하며, 새 violation이 이전에 고친 계약을 되돌리지 않도록 한다.
- **BR-11** canonical source facts 집합 밖의 citation/source reference는 유효하지 않다.
- **BR-12** repair와 regeneration budget은 로그에서 구분한다.

## 7. States and State Transitions

Story Plan preparation states:

- `GENERATING`: 최초 생성 또는 전체 재생성 중.
- `VALIDATING`: 후보를 구조·규칙·근거 기준으로 검사 중.
- `REPAIRING`: Repair Scope에 한정해 후보를 수정 중.
- `READY`: validation을 통과해 게시 가능한 상태.
- `BLOCKED`: repair 및 허용된 전체 재생성이 실패해 게시할 수 없는 상태.

전이:

- `GENERATING → VALIDATING`: 후보 생성 완료.
- `VALIDATING → READY`: violation 없음.
- `VALIDATING → REPAIRING`: 모든 미해결 violation이 Repairable이고 scope 계산 가능.
- `REPAIRING → VALIDATING`: scoped merge 완료.
- `VALIDATING → GENERATING`: scope 계산 불가 또는 merge 불가이며 Full Regeneration budget 미사용.
- `VALIDATING/REPAIRING/GENERATING → BLOCKED`: 허용된 시도 소진 또는 치명적 실패.

## 8. Failures, Exceptions, and Boundary Conditions

- scope 밖 변경이 provider 결과에 포함되어도 기존 값으로 되돌린다.
- violation이 여러 개면 scope union으로 한 번에 repair하되 scope 밖은 보존한다.
- scope를 식별할 수 없으면 Full Regeneration 1회 후 실패를 반환한다.
- Full Regeneration 후 다른 validation 오류가 생겨도 무제한 재생성하지 않는다.
- canonical source fact에 없는 citation 또는 지원되지 않는 combat participant reference는 게시를 허용하지 않는다.
- provider가 응답하지 않거나 결과가 파싱 불가능하면 해당 시도는 실패로 기록하고 정책에 따라 repair 또는 제한된 재생성으로 처리한다.
- 실패 응답에는 최소한 실패 단계, violation 코드·메시지, 시도 유형, 최종 상태를 포함한다.

## 9. Inputs and Outputs

입력: 초기 Story Plan 요청, Previous Full Candidate, 구조화된 validation violations, Repair Scope, canonical source facts, party·난이도·시나리오 context.

출력: 검증된 Story Plan 또는 `BLOCKED` 결과, 시도 유형별 로그, 누적 violation 진단, scope 및 merge 결과, 사용자 표시용 실패 원인.

## 10. Scope and Non-goals

범위: Story Plan violation 분류, deterministic Repair Scope, previous candidate 보존, repair-first retry, Scoped Merge, 제한된 Full Regeneration, citation/source fact 입력 계약, retry 진단 및 회귀 검증.

비범위: GM runtime 서술·전투 실행 변경, 전체 citation subsystem 재설계, 모델 fine-tuning, Best-of-N 생성, Bundle Lock/revision mismatch 수정, 새 Story Plan 도메인 기능.

## 11. Priorities and Trade-offs

1. 이미 검증된 Story Plan 보존과 게시 안전성이 창작 다양성보다 우선이다.
2. 가능한 경우 최소 범위 repair를 사용한다.
3. scope 불명확 시 전체 재생성은 한 번만 허용한다.
4. 재생성 성공률보다 무제한 변경으로 인한 연쇄 오류 방지를 우선한다.
5. 원인과 시도 유형의 관측 가능성을 속도보다 우선한다.

## 12. Success Conditions and Acceptance Criteria

- **AC-1** Burning Web rule/outcome 누락은 해당 stage scope의 Repairable violation으로 처리된다.
- **AC-2** `UNKNOWN_CITATION` 및 `UNSUPPORTED_COMBAT_PARTICIPANT`는 가능한 경우 전체 폐기 없이 repair된다.
- **AC-3** repair 요청에 Previous Full Candidate, structured violations, deterministic Repair Scope, canonical source facts가 포함된다.
- **AC-4** provider가 scope 밖 필드를 변경해도 Scoped Merge 후 기존 값이 유지된다.
- **AC-5** repair scope 계산 또는 merge가 불가능한 경우 Full Regeneration은 정확히 최대 1회다.
- **AC-6** Full Regeneration 실패 후 `REGENERATION_BUDGET_EXHAUSTED`로 종료하고 부분 계획을 게시하지 않는다.
- **AC-7** retry 로그가 INITIAL_GENERATION, REPAIR, FULL_REGENERATION을 구분하고 각 violation·scope를 기록한다.
- **AC-8** canonical source facts 밖의 citation/reference는 validation을 통과하지 못한다.
- **AC-9** candidate A → repair B → repair C → valid 흐름이 최종 `READY/PUBLISHED`가 된다.
- **AC-10** 기존 브라우저 자료로 Scenario Plan이 게시되고 5턴 진입 가능한 상태가 된다.
