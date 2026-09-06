# Product Spec: Story Runtime

## 1. Problem and Context

현재 모험 진행 모델은 `Adventure Story Plan`처럼 미래의 Stage, branch, ending을 비교적 구체적으로 미리 계획하고 그 계획을 런타임에서 따라가는 성격이 강하다. 이 방식은 플레이어 선택이 생길 때마다 미래 이야기를 다시 만들게 하거나, 반대로 이미 정한 사건 순서에 플레이어를 맞추게 만드는 문제가 있다.

이번 변경은 시나리오의 핵심 갈등과 Finale 방향은 보존하면서도, 실제 플레이 경로와 해결 방식은 플레이 중 발생하도록 Story Runtime을 재구성한다. 핵심은 **미래 사건 순서를 준비하는 대신, 현재 Stage가 플레이어에게 무엇을 발견하게 하고 어떤 위협을 진행시키며 어디까지 도달하면 다음 국면으로 넘어가는지를 준비하는 것**이다.

Storybook/RAG는 scenario fact의 근거이자 source of truth다. 시스템은 Storybook 전체를 거대한 구조화 Story Plan으로 복제하지 않는다. 전체 모험에는 얕은 Stage Backbone만 만들고, 현재 플레이에 필요한 Detailed Stage와 Situation을 점진적으로 준비한다.

플레이어 선택 때문에 매번 전체 미래를 재작성하지 않는다. Scenario가 의도하는 핵심 갈등과 Finale는 유지하고, 다양한 행동 경로를 Revelation, Threat/Pressure, Funnel, Situation으로 수용한다. 실제 Finale의 결과는 미리 정하지 않는다.

## 2. Goals and Desired Outcomes

- **G-1** 전체 모험을 미리 상세한 사건 순서로 생성하지 않고, 얕은 Stage Backbone과 현재 Stage의 상세 정보만 준비한다.
- **G-2** Storybook/RAG를 scenario fact의 근거로 유지하고, Stage/Situation은 source에서 파생되는 artifact로 취급한다.
- **G-3** 플레이어가 정해진 행동 순서를 따르지 않아도 Required Revelation과 Funnel을 여러 방법으로 달성할 수 있게 한다.
- **G-4** AI Game Master가 상황에 따라 Pressure와 Situation을 사용해 모험의 핵심 갈등 방향을 유지하되 플레이어의 구체적 선택은 강제하지 않는다.
- **G-5** 일반적인 예상 밖 행동 때문에 미래 Stage를 계속 재생성하지 않는다.
- **G-6** 플레이 결과 때문에 미래 Stage의 핵심 전제가 실제로 불가능해진 경우에만 아직 시작하지 않은 미래를 재계획한다.
- **G-7** Finale의 핵심 문제 또는 대면은 Scenario가 보존하지만 전투, 협상, 실패, 동맹 등 최종 결과는 플레이에서 결정되게 한다.
- **G-8** AI의 의미 판단과 deterministic Runtime의 canonical state authority를 분리한다.

## 3. Users and Actors

- **Solo Player**: 유일한 인간 플레이어. 자유롭게 행동을 선언하고, Stage가 기대한 특정 행동 순서를 따라야 할 의무가 없다.
- **AI Game Master**: 현재 플레이 맥락을 해석하고 Situation 선택, Pressure 적용 시점, Revelation 전달 여부, narration과 의미적 판단을 제안한다.
- **Stage Generation AI**: Scenario Source에서 Stage Backbone과 Detailed Stage를 생성한다.
- **Situation Generation AI**: 현재 Detailed Stage와 Runtime context에서 Stage 진행 목적을 가진 Situation을 생성한다.
- **Adventure Runtime**: 현재 Stage, Revelation, Pressure, Funnel, Situation 사용 상태 등 canonical narrative progression을 저장하고 deterministic invariant를 검증한다.
- **Scenario Source / Storybook RAG**: Scenario fact와 provenance를 제공한다.
- **Rulebook**: 규칙 판단이 필요할 때 demand-driven으로 조회하는 별도 근거다.

## 4. Ubiquitous Language

- **Scenario Source**: 하나의 모험에 사용하는 Storybook, Rulebook, map/asset 등 자료의 집합.
- **Storybook RAG**: Storybook 원문에서 필요한 scenario evidence를 조회하는 수단. Scenario fact의 최종 근거다.
- **Stage Backbone**: 모험 시작 전에 만드는 얕은 전체 구조. 주요 Stage, 각 Stage의 의미적 역할, 핵심 갈등과 Finale로 이어지는 방향만 담는다.
- **Stage**: 현재 모험에서 핵심 문제와 Situation 생성 목적이 의미적으로 동일한 하나의 국면. 문서 chapter, 고정 시간, turn 수로 나누지 않는다.
- **Detailed Stage**: 현재 플레이할 Stage를 위해 상세화된 숨겨진 GM용 artifact.
- **Core Problem**: 현재 Stage에서 다루는 중심 문제. 플레이어가 수행해야 하는 정답 행동 목록이 아니다.
- **Revelation**: 플레이어 캐릭터가 발견하거나 경험해야 하는 Scenario의 중요한 진실.
- **Required Revelation**: 정상 Funnel 진행에 중요하다고 Stage Generation AI가 지정한 Revelation.
- **Optional Revelation**: 모험 이해나 선택을 풍부하게 하지만 정상 Stage 전환에 필수는 아닌 Revelation.
- **Threat**: 플레이어와 무관하게 목적을 가지고 움직이는 적대 세력, 위험, 상황 또는 세계 변화.
- **Pressure**: Threat가 실제 세계에서 진행되고 있음을 보여 주는 단계적 변화 또는 사건 재료.
- **Funnel**: 현재 Stage에서 다음 국면으로 정상 전환하기 위한 명시적 조건. 특정 행동 순서가 아니라 정보, 관계, 위협 상태 같은 의미적 milestone을 표현한다.
- **Situation**: AI GM이 현재 Stage를 진행시키기 위해 사용할 수 있는 준비된 사건 기회 또는 문제 재료. 결과가 정해진 Scene이 아니다.
- **Stage Intent**: Situation이 현재 Stage를 진행시키는 목적. Revelation, Pressure, Funnel opportunity, 중요한 Consequence 중 하나 이상을 참조한다.
- **OPEN_PLAY**: 준비된 Situation을 활성화하지 않은 일반 플레이 상태. 탐색, 이동, 대화, 사소한 묘사 등을 AI GM이 자유롭게 처리한다.
- **Opening Situation**: 첫 Detailed Stage에서 모험을 시작할 때 한 번 사용하는 Situation.
- **Important Consequence**: 실제 플레이 결과 때문에 이후 Stage 진행에 의미 있게 영향을 주는 지속적 서사 결과.
- **Play-created Story Fact**: Scenario 원문이 아니라 실제 플레이 결과로 canonical하게 성립한 새로운 사실.
- **Premise Invalidation**: 아직 시작하지 않은 미래 Stage의 핵심 전제가 accepted play result 때문에 더 이상 성립할 수 없는 상태.
- **Finale**: Scenario의 핵심 갈등이 최종적으로 드러나거나 대면되는 국면. 해결 결과는 고정하지 않는다.

## 5. Core Use Cases

### UC-1 Stage Backbone을 생성한다

모험 시작 전 Stage Generation AI는 Storybook RAG를 넓게 조회해 시나리오의 핵심 갈등과 Finale 방향, 주요 국면을 파악하고 얕은 Stage Backbone을 생성한다. Stage 경계는 chapter나 분량이 아니라 Core Problem과 이후 Situation 생성 목적이 의미 있게 바뀌는 지점으로 정한다.

### UC-2 현재 Stage를 상세화한다

현재 필요한 Stage만 Detailed Stage로 확장한다. Detailed Stage는 최소한 Core Problem, Required/Optional Revelations, Threat/Pressure, Funnel, Situation generation guidance와 grounding 정보를 가진다. 첫 Stage에는 Opening Situation이 포함된다.

### UC-3 OPEN_PLAY를 진행한다

AI GM은 매 Turn마다 반드시 Situation을 사용할 필요가 없다. 준비된 Situation이 없는 동안에도 일반 탐색, 대화, 이동, 분위기 묘사와 사소한 상호작용을 처리할 수 있다. Stage 진행 목적이 없는 단순 flavor 사건을 Situation Pool에 강제로 넣지 않는다.

### UC-4 Stage-driving Situation을 사용한다

Situation Generator AI는 현재 Stage에서 필요한 목적을 보고 Situation을 만든다. 각 Situation은 Revelation, Pressure, Funnel opportunity, 중요한 Consequence 중 하나 이상의 Stage Intent를 가져야 한다. 하나의 Situation이 여러 Intent를 지원할 수 있다.

AI GM은 플레이 맥락에 맞는 Situation을 선택하고 사용할 시점을 결정한다. Situation은 플레이어에게 기회를 제시할 뿐 결과를 미리 정하지 않는다.

### UC-5 Revelation을 여러 방법으로 획득한다

같은 Revelation은 대화, 조사, 협박, 전투, 문서 확인, 관찰 등 여러 방식으로 전달될 수 있다. 특정 clue 또는 특정 Situation 하나가 실패했다고 Revelation 자체가 영구적으로 막히지 않는다. 필요하면 다른 Situation을 사용하거나 새 Situation을 생성한다.

Revelation이 실제로 플레이어 캐릭터에게 전달되었는지는 AI가 플레이 로그와 현재 맥락을 의미적으로 판단한다. Runtime은 해당 Revelation 참조와 현재 revision을 검증하고 상태를 canonical하게 저장한다.

### UC-6 Threat와 Pressure를 진행한다

Detailed Stage에는 Threat의 목적과 Pressure 재료가 준비된다. 실제 Pressure를 언제 적용할지는 AI GM이 플레이어 대응, 방치 정도, pacing, tension과 현재 세계 상태를 보고 판단한다. 고정된 N-turn 타이머로 자동 증가시키지 않는다.

플레이어가 Threat를 유효하게 막았으면 이후 Pressure는 skip, cancel 또는 의미에 맞게 변경될 수 있다.

### UC-7 Funnel을 만족해 다음 Stage로 이동한다

Stage Generation AI가 Funnel의 의미와 필요한 milestone을 정의한다. Runtime은 이미 canonical하게 확정된 Revelation 또는 narrative predicate를 사용해 Funnel 상태를 계산한다.

정상 전환은 Funnel이 충족되고 다음 Detailed Stage가 준비될 수 있을 때 발생한다. AI GM은 이를 서술하지만 canonical Stage transition을 직접 저장하지 않는다.

### UC-8 Stage를 해결하지 않고 이탈한다

플레이어가 후퇴, 포기, 지역 이탈, 세력 변경 등으로 Core Problem을 해결하지 않은 채 떠날 수 있다. 이 경우 unresolved exit으로 기록하고 미해결 Threat와 Consequence는 이후 Stage context에 남길 수 있다. 플레이어를 자동으로 같은 Stage에 되돌리지 않는다.

### UC-9 미래 Stage premise가 무효화되면 재계획한다

일반적인 예상 밖 행동이나 다른 조사 경로는 Backbone 재생성 이유가 아니다. 실제 플레이에서 canonical하게 성립한 사실 때문에 미래 Stage의 핵심 전제가 불가능해졌을 때만 AI가 Premise Invalidation을 제안한다.

유효한 경우 아직 시작하지 않은 미래 Stage suffix만 새 Backbone revision으로 다시 만든다. 완료된 Stage history와 이미 일어난 플레이 결과는 바꾸지 않는다.

### UC-10 Finale에 도달한다

Revelation, Pressure, Funnel을 통해 여러 플레이 경로가 Scenario의 핵심 Finale로 수렴할 수 있다. Finale의 핵심 갈등은 유지하지만 플레이어가 어떤 방식으로 맞서고 어떤 결과를 만드는지는 고정하지 않는다.

## 6. Business Rules and Invariants

- **BR-1** Storybook RAG는 scenario fact의 source of truth이며 Stage/Situation은 파생 artifact다.
- **BR-2** 전체 Scenario 내용을 거대한 구조화 Story Plan으로 복제하지 않는다.
- **BR-3** 전체 Stage Backbone은 얕게 준비하고 Detailed Stage는 현재 필요한 범위만 점진적으로 생성한다.
- **BR-4** Stage 경계는 의미적 변화로 정한다. chapter, turn 수, 고정 시간으로 정하지 않는다.
- **BR-5** Required/Optional Revelation과 Funnel 의미는 Stage Generation AI가 Scenario evidence를 보고 결정한다.
- **BR-6** Revelation 전달 여부는 AI 의미 판단 대상이며, Runtime은 참조와 state transition을 검증하고 저장한다.
- **BR-7** Pressure의 구조는 Detailed Stage에 준비하지만 실제 적용 시점은 AI GM이 결정한다. Runtime은 현재 Pressure state와 hard prerequisite만 검증한다.
- **BR-8** 준비된 Situation은 하나 이상의 Stage Intent를 가져야 한다.
- **BR-9** 결과가 정해진 Situation을 만들지 않는다. 플레이어 행동과 규칙 판정에 따라 결과가 결정된다.
- **BR-10** OPEN_PLAY는 정상 플레이 상태이며 Situation 부재 자체는 오류가 아니다.
- **BR-11** Stage가 끝나면 사용하지 않은 현재 Stage의 Situation은 폐기한다.
- **BR-12** 일반적인 플레이 편차로 Future Backbone을 재생성하지 않는다.
- **BR-13** 재계획은 Premise Invalidation이 발생한 미래의 unstarted suffix에만 적용한다.
- **BR-14** 완료된 Stage history와 accepted play-created fact는 재계획으로 수정하지 않는다.
- **BR-15** Scenario의 핵심 Finale를 보존하지만 Finale 결과는 미리 결정하지 않는다.
- **BR-16** AI는 의미 판단과 생성 제안을 담당하고 Runtime은 canonical state authority를 가진다.
- **BR-17** scenario-grounded 핵심 사실과 AI가 만든 connective detail을 구분한다. connective detail은 근거 없이 Required Revelation이나 Scenario canonical truth로 승격할 수 없다.

## 7. States and Transitions

Stage의 핵심 lifecycle은 `PLANNED → ACTIVE → COMPLETED` 또는 `PLANNED → ACTIVE → EXITED_UNRESOLVED`로 본다. Future Stage는 아직 시작 전이라면 Backbone replan으로 다른 revision에 대체될 수 있다.

Situation은 현재 Stage 안에서 `AVAILABLE → USED`가 기본이며, 플레이 상태와 모순되면 `INVALIDATED`될 수 있다. Stage가 종료되면 남은 AVAILABLE Situation은 폐기한다.

Revelation은 최소 `UNKNOWN → LEARNED`를 가지며, Pressure는 Detailed Stage에서 정의된 progression 중 현재 canonical 상태를 가진다. Funnel은 관련 predicate를 바탕으로 Runtime이 `NOT_SATISFIED / SATISFIED`를 계산한다.

OPEN_PLAY는 별도 무거운 lifecycle이 아니라 active Situation이 없는 일반 Runtime 상태다.

## 8. Failures, Exceptions, and Boundaries

- Required Revelation, Threat Core, mandatory Funnel fact, Finale core 등 grounding-required 정보에 유효한 source evidence가 없으면 해당 artifact를 정상 준비된 것으로 취급하지 않는다.
- Situation 생성이 실패해도 OPEN_PLAY가 가능하면 세션을 중단하지 않는다.
- 다음 Detailed Stage 생성이 실패하면 현재 Stage를 반쯤 종료한 상태로 만들지 않는다.
- AI의 오래된 판단이 최신 Runtime state를 덮어쓰면 안 된다.
- AI outage나 generation failure가 이미 확정된 canonical state를 되돌리거나 부분 변경하면 안 된다.
- Mechanical state는 각 owning service의 책임이며 Story Runtime이 복제 정본을 만들지 않는다.

## 9. Inputs and Outputs

주요 입력은 Scenario Package/Storybook evidence, 현재 Backbone revision, 현재 Detailed Stage, recent play, accepted narrative state, 관련 mechanical snapshot이다.

주요 출력은 Stage Backbone, Detailed Stage, Situation candidates, AI GM proposals, canonical Revelation/Pressure/Funnel state, Stage transition history, accepted Consequence와 Play-created Story Fact다.

플레이어에게는 내부 Stage planning 구조를 그대로 노출하지 않고 관찰 가능한 Scene과 선택 결과를 제공한다.

## 10. Scope and Non-goals

이번 범위에는 새 Story Runtime의 Stage/Revelation/Threat/Funnel/Situation 진행 모델과 이에 필요한 generation, state tracking, transition, selective replanning이 포함된다.

다음은 목표가 아니다.

- 전체 Storybook을 완전한 지식 그래프로 사전 구조화하기
- 플레이어 선택마다 전체 미래 스토리 다시 생성하기
- 특정 행동 순서를 강제하는 plot scripting
- 모든 ambient/flavor event를 Situation으로 사전 준비하기
- Finale 결과를 미리 정하기
- HP, spell slot, inventory, combat turn, map position 같은 mechanical canonical state를 Story Runtime으로 이전하기
- 별도 human GM workflow 추가하기

## 11. Priorities and Tradeoffs

1. 플레이어 agency를 유지하면서 Scenario의 핵심 갈등과 Finale 방향을 보존하는 것을 최우선으로 한다.
2. 미래 생성량을 줄이고 현재 Stage에 집중한다.
3. AI가 의미 판단에 강한 부분을 담당하되 canonical state 변경은 deterministic code가 통제한다.
4. 완전한 사전 구조화보다 필요 시 RAG 조회와 provenance를 우선한다.
5. 복잡한 lifecycle이나 별도 story service보다 기존 Adventure Runtime 경계 안에서 단순한 모델을 선호한다.

## 12. Acceptance Criteria

- 하나의 Scenario Source에서 얕은 Stage Backbone을 만들고 첫 Stage만 Detailed Stage로 준비해 모험을 시작할 수 있다.
- 첫 Stage에 Opening Situation을 한 번 제시한 뒤 Situation 없이 OPEN_PLAY를 계속할 수 있다.
- 현재 Stage에서 필요한 Situation을 점진적으로 생성할 수 있고 각 Situation은 하나 이상의 Stage Intent를 가진다.
- 같은 Required Revelation을 서로 다른 행동 경로로 획득해도 동일한 Funnel progression에 반영된다.
- AI GM이 Pressure 적용 시점을 결정하지만 Runtime이 invalid transition과 hard prerequisite 위반을 막는다.
- 플레이어가 예상 밖 행동을 해도 그것만으로 Future Backbone이 재생성되지 않는다.
- accepted play-created fact 때문에 미래 Stage premise가 실제로 불가능해지면 완료된 Stage를 유지한 채 미래 suffix만 새 revision으로 교체할 수 있다.
- Funnel 만족과 유효한 다음 Detailed Stage 준비가 함께 성립해야 정상 Stage transition이 완료된다.
- 플레이어는 Stage를 해결하지 않고 이탈할 수 있으며 미해결 결과는 이후 맥락에 남을 수 있다.
- Scenario-grounded 핵심 사실과 AI connective detail이 구분된다.
- Finale의 핵심 갈등은 유지되지만 실제 해결 방식과 결과는 플레이에서 결정된다.
