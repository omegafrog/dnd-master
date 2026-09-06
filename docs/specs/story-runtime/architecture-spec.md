# Architecture Spec: Story Runtime

## 1. Architecture Direction

### 다이어그램

- [Story Runtime 구조와 책임 경계](diagrams/architecture/story-runtime.class.svg)
- [AI 제안의 canonical 반영 상태](diagrams/architecture/runtime-proposal.state.svg)

이번 변경에서 새로운 deployable service는 추가하지 않는다.

기존 bounded context를 유지한다.

| 영역 | 책임 |
|---|---|
| Document Knowledge / `rule-knowledge-service` | Storybook·Rulebook 문서, extraction, Source Span, RAG 검색 |
| Scenario Preparation / `adventure-service` 내부 | Scenario Package, Stage Backbone, Detailed Stage 생성·검증·revision 관리 |
| Adventure Runtime / `adventure-service` 내부 | 현재 Stage 진행, Revelation/Pressure/Funnel/Situation canonical state, transition, replan 적용 |
| AI Game Master / `ai-game-master-service` | generation, semantic interpretation, Situation 선택, Pressure 판단, narration proposal |
| Dice / Character / Combat Map 등 | 각자의 mechanical canonical state |

핵심 원칙은 다음과 같다.

> AI는 의미를 결정하고 제안한다. Runtime은 유효성을 검증하고 canonical state를 확정한다.

AI가 직접 Adventure Runtime 저장소를 변경하는 경로는 만들지 않는다.

## 2. Scenario Package의 역할

`Scenario Package`는 Scenario Source에서 compile되어 publish된 immutable하고 grounded된 입력으로 유지한다.

이번 설계에서 Scenario Package를 거대한 Story Plan으로 확장하지 않는다. Stage Backbone, Detailed Stage, Situation은 Scenario Package와 Source Evidence를 입력으로 생성되는 별도 파생 artifact다.

책임 방향은 다음과 같다.

`Scenario Source → Scenario Package → Stage artifacts → Adventure Runtime`

기존 Progressive Scenario Compilation, Source Span, Active Source Context와 provenance 기반은 재사용한다.

## 3. Adventure Story Plan 교체

기존의 넓은 `Adventure Story Plan`은 새 Story Runtime의 canonical execution model로 사용하지 않는다. 다음 두 artifact로 역할을 분리한다.

### 3.1 Stage Backbone

전체 모험의 얕은 구조다. Stage 순서와 의미적 역할, 핵심 문제의 방향, Scenario-grounded Revelation/Threat/Finale 방향, 다음 국면으로 이어지는 Funnel 개요 정도를 가진다.

상세 NPC 행동, Scene 순서, encounter 순서, clue 배치처럼 플레이 중 정해져야 할 정보는 넣지 않는다.

### 3.2 Detailed Stage

실제로 플레이할 현재 Stage의 상세 artifact다.

최소 구성은 다음과 같다.

- Core Problem
- Required / Optional Revelations
- Threat Core
- Pressure progression material
- Funnel
- Situation generation guidance
- Source grounding
- 첫 Stage인 경우 Opening Situation

전체 Backbone은 미리 만들지만 Detailed Stage는 현재 필요한 Stage만 materialize한다.

## 4. Artifact Versioning

### 4.1 Scenario Package Version

기존 방식대로 immutable하다.

### 4.2 Stage Backbone Revision

publish된 Backbone revision은 수정하지 않는다. 초기 생성은 Revision 1이고, 플레이 결과 때문에 미래 Stage premise가 무효화되어 재계획하면 Revision 2를 만든다.

새 revision은 과거를 고치지 않는다. 아직 시작하지 않은 future suffix만 교체한다. 완료된 Stage는 자신이 실제 실행된 기존 revision과의 역사적 참조를 보존한다.

### 4.3 Detailed Stage Revision

Detailed Stage도 실행 기준 snapshot으로 취급한다. 플레이어가 예상 밖 행동을 했다는 이유로 현재 Detailed Stage 전체를 매 Turn 다시 생성하지 않는다.

새 Situation 생성, Revelation 상태, Pressure 상태, Consequence는 Runtime progression으로 처리한다.

## 5. Runtime Narrative State

Adventure Runtime은 narrative progression에 필요한 canonical state만 소유한다.

최소한 다음 상태가 필요하다.

- 현재 Backbone Revision
- 현재 Stage와 Detailed Stage Revision
- Stage lifecycle
- Revelation 상태
- 현재 Threat / Pressure 상태
- Funnel evaluation에 필요한 narrative predicates
- 현재 Stage의 Available / Used / Invalidated Situation 참조
- active Situation 참조 또는 없음
- accepted Play-created Story Facts
- important Consequences
- Stage exit reason
- 완료된 Stage history
- Runtime revision / version

다음 상태는 Adventure Runtime에 복제하지 않는다.

- HP
- spell slot과 기타 character resource
- inventory
- combat turn / initiative 정본
- character condition 정본
- tactical map position 정본

필요한 경우 해당 owning service의 snapshot 또는 확정 결과를 참조한다.

## 6. OPEN_PLAY와 Situation 표현

`OPEN_PLAY`와 `SITUATION_ACTIVE`를 별도 복잡한 상태 머신으로 만들 필요는 없다.

- `activeSituation = 없음`이면 OPEN_PLAY
- `activeSituation = 특정 Situation`이면 준비된 Situation을 활용 중

Situation이 끝나면 active reference를 비운다. Situation이 없어도 AI GM은 narration, NPC interaction, 이동, 탐색 등을 정상 처리한다.

## 7. Situation Pool Lifecycle

Situation Pool은 현재 Stage 전용 runtime material이다. 전체 모험용 Situation을 사전 생성하지 않는다.

Situation lifecycle은 최소한 다음으로 충분하다.

- `AVAILABLE`
- `USED`
- `INVALIDATED`

Stage가 종료되면 남아 있는 AVAILABLE Situation은 폐기한다. 실제 사용된 Situation과 그 결과는 Adventure history/log에 남긴다.

이전 Stage의 미사용 Situation을 장기 보존하는 retirement/archive subsystem은 만들지 않는다.

## 8. Situation Generation

Situation 생성 책임을 다음과 같이 나눈다.

### Scenario Preparation / Runtime orchestration

현재 Detailed Stage, Runtime state, Source context를 수집하고 generation request를 구성한다.

### Situation Generation AI

새 Situation이 지원할 Stage Intent와 사건 내용을 결정한다. Stage Intent는 하나 이상이어야 하며 다음을 참조할 수 있다.

- Revelation
- Pressure
- Funnel opportunity
- important Consequence

하나의 Situation은 여러 Intent를 지원할 수 있다.

### Runtime validation

생성된 Situation이 참조하는 Revelation, Pressure, Funnel, Consequence가 현재 Stage에 실제 존재하는지 검증한다. grounding-required claim에는 필요한 provenance가 있는지 검증한다.

### AI Game Master

실제 플레이 중 어느 Situation을 언제 사용할지 결정한다. 생성됐다는 이유만으로 즉시 실행하지 않는다.

Situation은 setup과 opportunity를 정의할 수 있지만 플레이 결과를 고정하지 않는다.

## 9. Opening Situation

별도 Opening Stage 모델은 만들지 않는다.

첫 Detailed Stage에만 Opening 역할을 가진 Situation 하나가 존재한다. Adventure Start preflight는 다음을 확인한다.

- First Stage materialization 가능
- Opening Situation 존재
- Opening의 Stage Intent 참조 유효
- grounding-required source reference 유효

Adventure Start 이후 Runtime은 Opening Situation이 한 번 제시되었음을 기록한다. 이후 일반 Situation/OPEN_PLAY 규칙을 사용한다.

## 10. Retrieval Strategy

### 10.1 Stage Backbone

Storybook 전체 구조를 이해할 수 있도록 비교적 넓은 structural retrieval을 수행한다. AI가 필요한 검색 질문을 제안할 수 있지만 deterministic code가 다음을 통제한다.

- 허용 Scenario Source Bundle
- document / revision scope
- retrieval budget와 policy
- provenance
- source validity

### 10.2 Detailed Stage

현재 Stage와 관련된 범위를 중심으로 focused retrieval한다. Backbone의 짧은 요약만 보고 canonical scenario detail을 창작하지 않는다.

### 10.3 Situation

현재 Detailed Stage와 최근 Runtime context를 기준으로 필요한 Source Evidence를 선택적으로 추가 조회한다.

### 10.4 Rulebook

규칙 판단이 필요할 때 demand-driven retrieval한다. Story 구조 생성을 위해 Rulebook 전체를 미리 읽지 않는다.

## 11. Grounding Model

모든 생성 문장을 동일한 수준으로 grounding할 필요는 없다. canonical importance가 높은 정보에 강한 grounding requirement를 둔다.

반드시 Scenario provenance가 있어야 하는 정보는 최소한 다음과 같다.

- Required Revelation
- mandatory Funnel을 구성하는 Scenario fact
- Threat Core
- Finale core
- 주요 canonical Scenario fact를 직접 주장하는 내용

AI-generated connective fiction은 Source Span 없이 허용할 수 있다. 다만 내부적으로 grounded scenario fact와 generated connective fact를 구별해야 한다.

connective fact는 근거 없이 Required Revelation이나 Scenario canonical fact로 승격할 수 없다.

## 12. Runtime Turn Processing

일반적인 GM Turn은 다음 책임 흐름을 따른다.

1. Adventure Runtime이 현재 narrative snapshot을 만든다.
2. 필요한 경우 Character, Combat, Map 등 owning service에서 mechanical context를 가져온다.
3. 현재 Active Source Context와 필요한 evidence를 구성한다.
4. AI Game Master에 현재 Stage, Revelation, Pressure, Funnel, Situation candidates, recent play를 제공한다.
5. AI Game Master는 narration과 semantic change를 proposal로 반환한다.
6. Runtime은 proposal을 deterministic하게 검증한다.
7. 허용된 변경만 canonical state에 commit한다.

AI proposal은 예를 들어 다음을 포함할 수 있다.

- narration
- Situation 선택/종료
- Revelation learned 판단
- Pressure progression
- new Consequence
- Play-created Story Fact
- unresolved Stage exit
- future premise invalidation
- mechanical command 요청

## 13. Proposal to Canonical Commit

모든 state-changing AI proposal은 최소한 자신이 판단할 때 본 Runtime revision과 대상 식별자를 포함해야 한다.

Runtime은 다음을 검증한다.

- Adventure Runtime이 동일 revision 또는 허용된 expected version인가
- 대상 Stage가 현재 Stage인가
- 참조 Revelation/Pressure/Situation/Funnel이 존재하는가
- 허용된 state transition인가
- hard prerequisite가 충족됐는가
- grounding-required 항목에 provenance가 있는가
- 동일 command/proposal이 이미 처리되지 않았는가

mechanical 변경은 owning service로 command를 위임하고 성공이 확정된 뒤 narrative consequence를 commit한다.

## 14. Revelation Resolution

Situation이 등장하거나 clue가 노출됐다는 이유만으로 Revelation을 자동 완료하지 않는다.

AI는 실제 플레이 로그와 context에서 캐릭터가 해당 사실을 충분히 접해 현재 알고 있다고 볼 수 있는지 판단하고 `Revelation learned` proposal을 만든다.

Runtime은 대상 Revelation, current Stage, Runtime revision을 검증하고 상태를 확정한다.

Revelation 획득 방법은 requirement가 아니다. 대화, 조사, 협박, 전투, 문서, 관찰 등 서로 다른 방법이 동일한 Revelation을 만족시킬 수 있다.

## 15. Threat / Pressure Processing

Detailed Stage에는 Scenario-grounded Threat Core와 Pressure progression material이 존재한다. Pressure는 예약된 script가 아니다.

AI GM은 현재 플레이를 보고 다음을 고려해 Pressure progression을 제안한다.

- 플레이어가 Threat를 얼마나 방치했는가
- 이미 어떤 대응을 했는가
- 현재 tension과 pacing
- world state
- 이전 Pressure 결과

Runtime은 허용된 transition과 hard prerequisite만 검증한다.

플레이어 행동으로 미래 Pressure가 무의미해졌다면 AI는 해당 step을 skip, cancel 또는 replace할 수 있다. `N turns → 다음 Pressure` 같은 deterministic story timer는 사용하지 않는다.

## 16. Funnel Evaluation and Stage Transition

Funnel의 의미는 Detailed Stage 생성 시 Stage Generation AI가 정의한다. 매 Turn AI가 Funnel 의미를 다시 작성하지 않는다.

Funnel은 Runtime이 평가할 수 있는 canonical narrative predicate를 참조한다. 예를 들면 다음과 같다.

- 특정 Required Revelation learned
- target location known
- current threat neutralized
- access path established

`target location known` 같은 semantic fact 자체를 canonical하게 만드는 과정에는 AI interpretation과 Runtime acceptance가 필요할 수 있다. 일단 predicate가 확정된 뒤 Funnel 만족 여부 계산은 Runtime이 담당한다.

Funnel이 충족되어도 canonical Stage transition authority는 Runtime에 있다. AI GM은 전환을 서술하고 제안할 수 있지만 직접 저장하지 않는다.

## 17. Next Stage Materialization

미래 Stage 전체를 처음부터 Detailed Stage로 만들지 않는다. 현재 Stage 정상 exit가 가까워졌거나 transition이 확정되는 시점에 다음 Stage를 materialize한다.

중요 invariant는 다음과 같다.

> valid next Detailed Stage가 준비되기 전에 current Stage를 canonical하게 종료하지 않는다.

다음 Stage 생성 또는 grounding validation이 실패하면 현재 Runtime state를 손상시키지 않고 transition을 보류한다.

## 18. Unresolved Stage Exit

플레이어가 Core Problem을 해결하지 않고 후퇴, 지역 이탈, 포기, 세력 변경 등으로 떠날 수 있다.

AI GM은 실제 플레이 맥락에서 unresolved exit을 제안한다. Runtime은 current Stage와 player-intent evidence, runtime reference를 검증하고 exit reason을 기록한다.

해결되지 않은 Threat와 Consequence는 이후 Detailed Stage generation context에 포함할 수 있지만 자동으로 플레이어를 같은 Stage로 되돌리지 않는다.

## 19. Premise Invalidation and Backbone Replanning

Backbone 수정은 일반 Turn 처리의 일부가 아니다.

AI가 accepted play result를 기준으로 **아직 시작하지 않은 Future Stage의 핵심 premise가 더 이상 성립할 수 없음**을 판단했을 때만 Premise Invalidation proposal을 만든다.

단순히 예상과 다른 길로 조사하거나 Situation을 건너뛴 것은 invalidation이 아니다.

Runtime은 다음을 검증한다.

- 대상이 미래의 unstarted Stage인가
- 현재 Backbone revision과 일치하는가
- 완료된 Stage를 수정하려 하지 않는가
- accepted Play-created Story Fact 또는 canonical state가 근거로 존재하는가

유효하면 Scenario Preparation에 future suffix replanning을 요청한다.

## 20. Replanning Result

Replanner 입력은 다음을 포함한다.

- 기존 Scenario Package
- 현재 Backbone revision
- 완료된 Stage history
- accepted Play-created Story Facts
- unresolved Consequences
- Premise Invalidation reason
- 보존해야 하는 Scenario-grounded core conflict와 Finale

AI는 무효화된 미래 구간부터 suffix만 다시 만든다. 결과는 기존 Backbone을 overwrite하지 않고 새 Backbone Revision으로 publish한다.

Runtime은 optimistic version check에 성공한 경우에만 새 revision으로 binding을 전환한다.

## 21. Play-created Story Facts

Scenario 원문에 없지만 실제 플레이 결과로 지속적 사실이 성립할 수 있다. 예를 들어 원래 적대적인 NPC와 플레이어가 실제 플레이를 통해 동맹을 맺는 경우다.

이 정보는 단순한 AI connective fiction과 구별한다. AI가 fact proposal을 만들고 Runtime이 실제 플레이 근거와 current revision을 확인한 뒤 accept한다.

accepted Play-created Story Fact는 이후 Situation generation과 future replanning input으로 사용할 수 있지만 Scenario Source 자체를 수정한 것으로 취급하지 않는다.

## 22. Failure Handling

### Grounding validation failure

Required Revelation, Threat Core, mandatory Funnel fact, Finale core 등에 유효한 evidence가 없으면 해당 artifact를 publish하지 않는다. AI가 그럴듯하게 생성했다는 이유로 자동 채택하지 않는다.

### Stale AI response

AI 요청 이후 Runtime revision이 바뀌었다면 오래된 state-changing proposal을 reject한다. 필요하면 최신 snapshot으로 다시 판단한다.

### Situation generation failure

usable Situation이 없어도 OPEN_PLAY가 가능하면 Adventure를 실패시키지 않는다. 이후 refill을 재시도할 수 있다.

### Next Stage generation failure

current Stage 종료와 next Stage 시작 사이에 half-transition을 만들지 않는다.

### Replan conflict

현재 Backbone revision이 요청 당시와 다르면 replan result를 overwrite하지 않는다.

### AI outage

이미 저장된 canonical Runtime state는 그대로 유효해야 하며 부분 state change를 남기지 않는다.

## 23. Concurrency and Idempotency

Runtime Turn, Revelation/Pressure update, Stage transition, Backbone revision switch는 versioned optimistic concurrency를 기본으로 한다.

모든 state-changing proposal 또는 command는 자신이 생성될 때 본 expected Runtime revision을 검증한다.

동일 command 재전송으로 중복 Revelation, 중복 Pressure progression, 중복 Stage exit이 발생하지 않도록 idempotency key 또는 기존 command identity pattern을 재사용한다.

현재 요구사항만으로 별도 distributed workflow framework는 도입하지 않는다.

## 24. AI Game Master Service Boundary

`ai-game-master-service`에 storage authority를 추가하지 않는다.

AI가 담당하는 generation/interpretation은 다음과 같다.

- retrieval query 제안
- Stage Backbone generation
- Detailed Stage generation
- Situation generation
- Revelation interpretation
- Pressure timing 판단
- Premise Invalidation 판단
- narration과 player action interpretation

모든 state-changing 결과는 proposal이며 Adventure canonical progression authority는 `adventure-service`에 남는다.

## 25. Scenario Preparation and Adventure Runtime Boundary

둘 다 현재처럼 `adventure-service` 내부 bounded context로 유지한다.

### Scenario Preparation

앞으로 사용할 narrative artifact를 만든다.

- Stage Backbone
- Detailed Stage
- grounding validation
- artifact revision
- future suffix replan
- provenance

### Adventure Runtime

실제로 플레이되어 확정된 상태를 관리한다.

- current Stage
- Revelation state
- Pressure state
- Funnel state
- Situation usage
- Play-created Story Facts
- Consequences
- Stage history
- Stage transition
- Runtime Binding

별도 story microservice로 분리하지 않는다.

## 26. Existing Runtime Integration

기존 `Runtime Binding`, preflight, `Active Source Context`, Runtime Turn/Command orchestration 구조는 유지한다.

`RuntimeTurnCoordinator` 계열 orchestration은 mechanical service와 AI GM 사이를 조정하는 기존 역할을 계속 사용한다.

`Runtime Binding`은 Scenario Package뿐 아니라 현재 Stage Backbone Revision과 Detailed Stage Revision을 명확하게 식별할 수 있어야 한다.

Scenario compilation/application 영역은 Stage artifact generation과 publish orchestration으로 확장하고, 기존 Source Anchor validation과 Rulebook retrieval 경로를 재사용한다.

## 27. Preflight

Adventure 시작 가능 여부에는 최소한 다음 조건을 포함한다.

- valid published Scenario Package
- valid Stage Backbone
- First Detailed Stage materialization 가능
- Opening Situation 존재
- grounding-required core element의 유효한 Source reference
- Runtime Binding이 artifact revision을 명확히 가리킴

모든 미래 Detailed Stage가 이미 생성되어 있을 필요는 없다.

## 28. Observability

AI 의미 판단이 canonical state에 영향을 주므로 결과만 저장하면 디버깅하기 어렵다. 각 narrative state change에는 최소한 다음 traceability를 남긴다.

- GM Turn / command identifier
- AI proposal identifier
- expected Runtime revision
- 관련 Stage/Situation/Revelation/Pressure identifier
- grounding-required인 경우 Source Span reference
- accept / reject 결과
- deterministic reject reason

AI의 private chain-of-thought 저장은 요구하지 않는다. 재현과 감사에 필요한 structured rationale와 reference만 보존한다.

## 29. Architecture Invariants

1. AI가 canonical Adventure state를 직접 저장하지 않는다.
2. Scenario-grounded requirement를 AI connective fiction으로 대체하지 않는다.
3. 완료된 Stage history는 Backbone replan으로 변경되지 않는다.
4. 일반적인 플레이 편차는 Backbone regeneration을 발생시키지 않는다.
5. Future Stage Premise Invalidation만 future suffix replanning trigger가 된다.
6. Replan은 아직 시작하지 않은 미래 suffix만 교체한다.
7. Mechanical canonical state를 Adventure Runtime에 복제하지 않는다.
8. 현재 필요 범위가 아니면 Detailed Stage와 Situation을 선행 상세 생성하지 않는다.
9. OPEN_PLAY는 Situation 없이도 정상 동작한다.
10. Opening Situation은 Adventure 시작에서 한 번만 사용한다.
11. Stage transition은 Runtime이 확정한다.
12. 다음 Stage 생성 실패가 half-transition을 만들지 않는다.
13. stale AI proposal은 최신 Runtime state를 overwrite하지 않는다.
14. Required Revelation, mandatory Funnel fact, Threat Core, Finale core에는 provenance가 존재한다.
15. accepted Play-created Story Fact와 AI connective fiction을 구별한다.

## 30. Architecture Acceptance Criteria

- 같은 Scenario Package에서 Stage Backbone을 생성하고 첫 Stage만 상세화해 Adventure를 시작할 수 있다.
- Opening Situation을 한 번 사용한 뒤 Situation 없이 OPEN_PLAY를 계속할 수 있다.
- 현재 Stage에 필요한 Situation을 점진적으로 생성하고 Runtime이 Stage Intent reference를 검증할 수 있다.
- 서로 다른 플레이 방법으로 같은 Required Revelation을 얻어도 동일한 canonical Revelation state와 Funnel progression으로 이어진다.
- Pressure는 AI GM이 의미적으로 진행시키지만 Runtime이 invalid transition, hard prerequisite violation, stale update를 차단한다.
- Funnel이 충족되어도 valid next Detailed Stage가 준비되어야 atomic Stage transition이 가능하다.
- 예상 밖 행동 자체는 Future Backbone을 변경하지 않는다.
- accepted Play-created Story Fact 때문에 Future Stage premise가 불가능해지면 완료된 Stage를 보존하면서 future suffix만 새 Backbone Revision으로 교체할 수 있다.
- Scenario에 없는 connective detail을 생성할 수 있지만 provenance 없이 Required Revelation이나 Scenario canonical truth로 승격할 수 없다.
- Character, Combat, Dice, Map 등 기존 owning service의 canonical authority를 침범하지 않는다.
