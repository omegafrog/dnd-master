# Product Spec: TurnPlan structured narrative IR

## 1. Problem and Context

AI Game Master가 한 GM Turn을 자연어 응답으로 바로 만들면 플레이어 의도 해석, 판정 필요성, 공개 정보, 상태 변화, Adventure Story Plan 진행, 문장 생성 판단이 한 결과에 섞인다. 실패 원인을 추적하거나 규칙·비밀 정보·플레이어 agency 경계를 구조적으로 검증할 수 없다.

`TurnPlan`은 최종 GM prose가 아닌, 한 Turn의 실행 의미를 표현하는 구조화된 중간 표현(IR)이다. Adventure Story Plan의 장기 진행을 대체하지 않는다. Story Plan은 “어디로 가는가”, TurnPlan은 “이번 Turn에서 무엇을 하고 얼마나 진행하는가”를 담당한다.

## 2. Goals and Desired Outcomes

- G-TP-001: prose 없이 한 GM Turn 의미를 구조화해 표현한다.
- G-TP-002: 플레이어 의도, 판정 요청, 서사 의도, 정보 경계, 상태 효과, 이야기 진행을 독립 책임으로 분리한다.
- G-TP-003: 정보 공개 경계와 Player Agency를 IR 수준에서 검증한다.
- G-TP-004: 안정된 JSON 계약과 `schemaVersion: "1"`를 제공한다.
- G-TP-005: 대표 TurnPlan fixture와 구조적 validation으로 계약을 회귀 검증한다.

## 3. Users and Actors

- **Solo Player**: 행동 하나를 입력하고, 자신의 행동 밖 의사결정이 확정되지 않은 GM Turn을 받는다.
- **AI Game Master**: 미래 Planner가 TurnPlan 후보를 생성할 때 의미 계약을 따른다. 이번 범위에서 Planner orchestration은 제공하지 않는다.
- **Adventure Runtime**: 미래에 유효한 TurnPlan을 규칙 실행·상태 확정·서술 단계로 전달한다. 이번 범위에서 실제 handoff는 제공하지 않는다.
- **TurnPlan Consumer**: fixture, serialization, validation을 통해 IR 계약을 소비하는 내부 역할이다.

## 4. Ubiquitous Language and Terminology

- **TurnPlan**: 한 GM Turn의 실행 의미를 표현하는 versioned IR. prose, dialogue, 판정 결과를 포함하지 않는다.
- **PlayerIntent**: 입력된 플레이어 행동, 목표, 대상의 의미. 성공 여부나 입력에 없는 추가 행동을 포함하지 않는다.
- **ResolutionRequest**: 규칙 판정 필요성, 유형, 관련 능력/기술, 대상, 이유를 표현하는 요청. roll·성공/실패·damage 등 확정 결과는 제외한다.
- **NarrativeIntent**: 장면 목적, tone, pacing 같은 Turn의 서사 기능. 문체나 최종 문장이 아니다.
- **InformationPolicy**: 이번 Turn에 필수·공개 가능·공개 금지인 정규화된 Fact ID 경계.
- **StateEffect**: Turn 의미상 의도된 상태 변화. 실제 저장·commit·기존 Rule Engine 상태 중복은 정의하지 않는다.
- **StoryProgress**: 현재 Adventure Story Plan stage와 condition에 대한 Turn-level 진행 신호. 장기 Story Plan을 생성·수정하지 않는다.
- **Fact ID**: 정보 경계 비교용 안정된 식별자. v1은 Fact 본문·근거 연결을 소유하지 않는다.

## 5. Core Use Cases

### UC-TP-001: 규칙 판정 없는 관찰 Turn 표현

1. Solo Player가 “벽화를 자세히 본다”를 입력한다.
2. Consumer는 PlayerIntent와 NarrativeIntent, InformationPolicy를 가진 TurnPlan을 만든다.
3. `resolutionRequests`는 비어 있다.
4. validation은 구조와 정보 경계를 확인한다.

### UC-TP-002: 판정 요청 Turn 표현

1. Solo Player가 문 너머 소리를 듣는다.
2. TurnPlan은 해당 행동·목표·대상과 Perception 기반 ResolutionRequest를 표현한다.
3. TurnPlan은 roll 값, 성공 여부, 최종 결과를 포함하지 않는다.
4. validation 성공한 TurnPlan은 JSON으로 round-trip 가능하다.

### UC-TP-003: 정보 비대칭 표현

1. 세계에는 문 뒤 Goblin과 함정 정보가 존재하지만 Solo Player는 모른다.
2. TurnPlan은 공개 가능한 Fact ID와 금지 Fact ID를 분리한다.
3. 같은 Fact ID가 공개 집합과 금지 집합에 함께 있으면 validation이 거부한다.

### UC-TP-004: 상태 변화와 Story 진행 표현

1. Solo Player가 레버를 당기거나 지하 입구를 발견한다.
2. TurnPlan은 필요한 StateEffect 또는 StoryProgress condition을 표현한다.
3. IR은 실제 상태를 저장하거나 Story Plan을 수정하지 않는다.

## 6. Business Rules and Invariants

- BR-TP-001: TurnPlan은 PlayerIntent, ResolutionRequest 목록, NarrativeIntent, InformationPolicy, StateEffect 목록, StoryProgress를 가진다.
- BR-TP-002: `schemaVersion`은 필수이며 v1 값은 `"1"`이다.
- BR-TP-003: `PlayerIntent`는 비어 있을 수 없고, 입력에 없는 행동·감정·의사결정을 추가하지 않는다.
- BR-TP-004: ResolutionRequest는 요청 의미만 담는다. 결과·roll·damage·확정 상태는 표현할 수 없다.
- BR-TP-005: `requiredFacts ∩ forbiddenFacts = ∅`; `revealableFacts ∩ forbiddenFacts = ∅`.
- BR-TP-006: InformationPolicy fact 목록은 정규화된 Fact ID만 포함하며 중복되지 않는다.
- BR-TP-007: target과 Story condition은 형식과 공백만 검증한다. 실제 객체·현재 Story Plan 존재 여부는 검증하지 않는다.
- BR-TP-008: 확장되는 행동 식별자는 열린 value/string으로 유지한다. 안정된 작은 분류만 enum으로 제한한다.
- BR-TP-009: TurnPlan은 최종 GM prose/dialogue를 저장하지 않는다.
- BR-TP-010: StoryProgress는 현재 Story Plan을 참조할 수 있지만 새 장기 Story Plan을 만들거나 수정하지 않는다.

## 7. States and State Transitions

TurnPlan v1은 영속 aggregate나 runtime lifecycle을 만들지 않는다.

| Current state | Operation | Next state |
| --- | --- | --- |
| candidate data | structural validation passes | valid TurnPlan |
| candidate data | structural validation fails | validation error; no TurnPlan output |
| valid TurnPlan | serialization/deserialization | equivalent valid TurnPlan |

## 8. Failures, Exceptions, and Boundary Conditions

- 필수 필드·빈 PlayerIntent·공백 식별자 → validation error.
- fact 집합 충돌 또는 중복 → validation error.
- ResolutionRequest에 확정 결과를 표현하는 구조 → schema/model validation이 거부한다.
- 잘못된 target/Story condition 형식 → validation error.
- 빈 목록은 판정·상태 효과·진행 없음으로 유효할 수 있다.
- 실제 target, Fact, Story condition 존재성은 runtime integration 전까지 확인하지 않는다.

## 9. Inputs and Outputs

| Input | Output |
| --- | --- |
| Player action 의미와 현재 Turn 의도 | valid 또는 invalid TurnPlan candidate |
| TurnPlan object | versioned JSON serialization |
| JSON TurnPlan payload | valid TurnPlan 또는 validation error |

## 10. Scope and Non-goals

### In scope

- TurnPlan v1 domain model, JSON serialization contract, structural validation.
- 대표 fixture/test case: 단순 관찰, 판정 요청, 정보 비대칭, 상태 변화, Story 진행.

### Out of scope

- Planner/Writer 역할 분리와 LLM orchestration.
- Rule Engine 실제 실행 handoff, roll·damage·상태 commit.
- runtime narrative state, NPC knowledge memory.
- Writer 입력 계약, 최종 GM prose 생성.
- Eval, Critic, Best-of-N, prompt optimization, fine-tuning.
- DB 저장, API endpoint, runtime 생성 경로.
- Fact 본문·근거 연결, 외부 참조 존재성 검증, schema migration 정책.

## 11. Priorities and Trade-offs

- 구조 검증 우선 → runtime 의미 검증은 후속 integration으로 분리.
- Fact ID 사용 → 경계 충돌을 기계 검증 가능; 사람용 문구와 evidence ownership은 보류.
- 열린 action identifier → v1 enum 폭발 방지; 일부 분류만 enum으로 안정화.
- version marker 필수 → 진화 출발점 확보; backward compatibility 정책은 아직 고정하지 않음.

## 12. Success Conditions and Acceptance Criteria

- [ ] 한 GM Turn 의미를 final prose 없이 TurnPlan으로 표현한다.
- [ ] 6개 영역이 독립 책임으로 모델링된다.
- [ ] 판정 요청과 판정 결과가 데이터 모델에서 분리된다.
- [ ] Fact ID 기반 공개 가능/금지 정보 경계를 구조적으로 검증한다.
- [ ] StateEffect와 StoryProgress를 Turn 단위로 표현한다.
- [ ] 모든 payload에 `schemaVersion: "1"`를 직렬화한다.
- [ ] 불변식·형식 위반은 validation error가 된다.
- [ ] 대표 5개 fixture/test case가 계약을 검증한다.
- [ ] DB/API/Planner/Writer/Rule Engine handoff는 추가되지 않는다.
