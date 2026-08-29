# Product Spec: Planner와 Writer 분리 GM 턴 생성

## 1. Problem and Context

단일 AI Game Master가 플레이어 의도 해석, 사건·NPC 반응·공개 범위·기계 판정 결정과 prose 작성을 동시에 하면, 표현 중 새 세계 사실·사건·상태 변경을 암묵적으로 만들 수 있다. Writer가 미래 계획이나 비밀을 보면 지시문만으로 누설을 막기 어렵다.

Planner는 무슨 일이 일어날지 결정한다. Writer는 그 결정만 플레이어-facing prose로 풍부하게 표현한다. Writer는 비밀과 미래 계획을 알지 못하고 canonical world state를 변경하지 않는다.

## 2. Goals and Desired Outcomes

- G-PLAN-001: Planner가 의도, 사건, NPC 반응, 공개 범위, progression, mechanical request를 단독 결정한다.
- G-WRITE-001: Writer가 승인된 개요와 공개 장면 정보로 narration, dialogue, sensory description, tone을 만든다.
- G-BOUNDARY-001: WriterContext에서 future event, unrevealed secret, hidden NPC motivation, 불필요한 raw RAG를 제외한다.
- G-STATE-001: Writer 교체·재시도·표현 차이가 같은 ResolvedTurnPlan의 world transition을 바꾸지 않는다.
- G-RETRY-001: Writer 실패는 같은 ResolvedTurnPlan을 쓰는 presentation failure로 처리한다.

## 3. Users and Actors

- **Solo Player**: 행동을 입력하고, 현재 공개 가능한 정보만 반영한 응답을 받는다.
- **Planner**: 넓은 runtime context로 turn decision을 만든다.
- **Rules/Runtime Engine**: mechanical result와 canonical world transition을 권위 있게 해결한다.
- **Writer**: ResolvedTurnPlan을 prose로 표현한다. 저장 권한이 없다.
- **Adventure Runtime**: Planner, Engine, Writer를 조정하고 failure를 관리한다.

## 4. Ubiquitous Language and Terminology

- **TurnPlan**: Planner가 만든 한 GM Turn의 결정 계약. player intent, 사건, NPC 반응, narrative intent, information policy, mechanical request를 포함한다.
- **ResolvedTurnPlan**: Engine 판정과 권위 있는 world transition이 반영된 TurnPlan. Planner → Writer의 유일한 narrative decision contract다.
- **PlannerContext**: player input, world/scene/story state, NPC/player knowledge, relevant memory, RAG, rule operation을 포함할 수 있는 Planner 판단 입력.
- **WriterContext**: ResolvedTurnPlan, visible scene information, relevant character/style information, writing configuration만 담는 최소 표현 입력.
- **Information Policy**: `revealableFacts`와 `forbiddenFacts`로 공개 허용·금지 사실을 정하는 Planner 결정.
- **Canonical Fact**: runtime state 또는 narrative memory에 저장된 권위 있는 세계 사실.
- **Ephemeral Local Detail**: 조명, 먼지, 냄새, 온도, 메아리, 말투처럼 풍부하지만 영속·기계·정체성·위치·단서·관계·수량·인과 의미를 더하지 않는 표현 detail.
- **Presentation Failure**: Writer가 prose를 만들지 못한 상태. Planner 결정과 canonical state를 변경하지 않는다.

## 5. Core Use Cases

### UC-PLAN-001: 플레이어 행동을 계획하고 표현한다

1. Solo Player가 행동 또는 질문을 입력한다.
2. Planner가 PlannerContext로 의도, 다음 사건, NPC 반응, 공개·비공개 사실, narrative intent, mechanical request를 결정한다.
3. Engine이 판정을 해결하고 canonical transition이 반영된 ResolvedTurnPlan을 만든다.
4. Writer가 WriterContext와 ResolvedTurnPlan만 받아 prose를 생성한다.
5. Runtime이 prose와 확정 turn result를 반환한다.

### UC-WRITE-001: 같은 결정으로 다른 표현을 만든다

1. Runtime이 같은 ResolvedTurnPlan을 Writer에 전달한다.
2. Writer model/prompt가 달라도 사건, 공개 사실, 기계 결과, transition은 유지된다.
3. 문장, 리듬, 감각 표현은 달라질 수 있다.
4. local detail은 명시적 승격 없이는 canonical state나 narrative memory에 저장되지 않는다.

### UC-WRITE-002: Writer 실패를 재시도한다

1. Writer 호출이 실패하거나 prose를 만들지 못한다.
2. Runtime이 Presentation Failure를 반환한다.
3. 동일 ResolvedTurnPlan으로 Writer만 재호출한다.
4. 새 player input 없이는 Planner와 Engine을 재실행하지 않는다.

## 6. Business Rules and Invariants

- BR-PLAN-001: Planner만 player intent, 사건, NPC 반응, 공개 범위, progression, mechanical request를 결정한다.
- BR-PLAN-002: Planner는 player-facing prose를 작성하지 않는다.
- BR-WRITE-001: Writer는 ResolvedTurnPlan에 없는 entity, event, canonical fact, player decision, mechanical result, revelation, progression을 만들 수 없다.
- BR-WRITE-002: Writer는 `forbiddenFacts` 또는 그 사실을 알 수 있는 context를 받지 않는다.
- BR-WRITE-003: WriterContext는 future stage, unrevealed StoryPlan, hidden fact, hidden NPC motivation, 불필요한 raw RAG를 포함할 수 없다.
- BR-WRITE-004: Writer는 Ephemeral Local Detail만 추가할 수 있다. 불확실하면 생략한다.
- BR-STATE-001: canonical state transition은 Planner/Engine 소유이며 Writer output으로 생성·변경·승격되지 않는다.
- BR-STATE-002: 같은 ResolvedTurnPlan의 canonical fact, event, state mutation은 Writer model·prompt·retry로 달라질 수 없다.
- BR-RETRY-001: Presentation Failure는 state를 바꾸지 않으며 Writer만 같은 ResolvedTurnPlan으로 retry한다.

## 7. States and State Transitions

`REQUESTED → PLANNING → RESOLVING → RESOLVED → WRITING → PRESENTED`

- Planner/Engine failure: `PLANNING | RESOLVING → FAILED_RETRYABLE`; ResolvedTurnPlan 없음.
- Writer failure: `WRITING → PRESENTATION_FAILED_RETRYABLE`; ResolvedTurnPlan과 canonical transition 유지.
- Retry: `PRESENTATION_FAILED_RETRYABLE → WRITING`.
- New player input: 새 `REQUESTED`; 이전 turn을 재계획하지 않는다.

## 8. Failures, Exceptions, and Boundary Conditions

- Planner가 공개하지 않은 사실은 WriterContext에서 제거한다.
- `금속이 부딪히는 소리`만 허용되면 Writer는 `검을 가는 소리`, `두 고블린`, 보물 같은 구체 canonical interpretation을 만들 수 없다.
- Writer는 새 NPC, item, hidden passage, clue, attack, mechanical result를 추가할 수 없다.
- Writer failure는 중립 문장이나 재계획으로 숨기지 않는다.
- Critic/Verifier와 prose fact extraction은 범위 밖이다. #205는 context boundary와 responsibility contract를 제공한다.

## 9. Inputs and Outputs

### Inputs

- Solo Player input
- PlannerContext
- WriterContext

### Outputs

- TurnPlan, ResolvedTurnPlan
- Planner/Engine 소유 canonical world transition
- Writer-generated player-facing prose
- Presentation Failure와 retry result

## 10. Scope and Non-goals

### Scope

- Planner/Writer 책임·context·output contract 분리
- TurnPlan/ResolvedTurnPlan을 Planner → Writer boundary로 사용
- local detail 허용 범위와 canonical state 비승격
- Writer presentation failure 독립 retry

### Non-goals

- TurnPlan IR 필드 신규 설계
- Narrative Memory/NPC Knowledge 모델 재설계
- Critic/Verifier, prose fact extraction, best-of-N planning
- Style retrieval, prompt optimization, fine-tuning
- Writer prose의 자동 canonical fact 승격

## 11. Priorities and Trade-offs

1. **비밀 격리 우선**: Writer에게 비밀을 주지 않는다.
2. **결정 안정성 우선**: prose richness보다 Planner/Engine transition 불변성을 우선한다.
3. **표현 품질 허용**: 문장·리듬·감각 detail은 달라질 수 있다.
4. **재시도 범위 최소화**: Writer failure가 planning/rules를 다시 실행하지 않는다.

## 12. Success Conditions and Acceptance Criteria

- AC-PLAN-001: Planner가 사건, NPC 반응, 공개 범위, progression, mechanical request를 결정하고 Writer가 prose만 만든다.
- AC-WRITE-001: 같은 ResolvedTurnPlan에서 prose는 달라도 canonical facts, events, state mutations는 동일하다.
- AC-WRITE-002: Writer는 plan에 없는 entity, event, canonical fact, player decision, mechanical result를 추가하지 않는다.
- AC-BOUNDARY-001: WriterContext에 unrevealed StoryPlan, future event, hidden fact, hidden NPC motivation이 없다.
- AC-BOUNDARY-002: Writer는 raw RAG 대신 revealable/required facts만 받는다.
- AC-DETAIL-001: local detail은 canonical state 변경이나 memory commit을 만들지 않는다.
- AC-STATE-001: Writer model/prompt 교체가 같은 ResolvedTurnPlan의 world transition을 바꾸지 않는다.
- AC-RETRY-001: Writer failure는 같은 ResolvedTurnPlan으로 Writer만 retry한다.
