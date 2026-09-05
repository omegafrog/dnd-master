# Product Spec: Scenario Model Runtime

## 1. Problem and Context

현재 모험 진행의 핵심 모델은 `Adventure Story Plan`처럼 시작 전에 Stage, branch, ending을 미리 구성하고 런타임이 그 계획을 진행하는 방식이다. 이 방식은 플레이어 행동에 따라 실제 이야기가 만들어지는 TRPG의 성격보다 미리 정한 미래 순서를 실행하는 쪽에 가깝고, 런타임이 현재 세계 상태에서 자연스럽게 다음 문제를 구성하기 어렵게 만든다.

이번 변경은 기존 `Adventure Story Plan` 흐름을 보완하는 것이 아니라 **완전히 대체**한다. Storybook에서 시나리오의 핵심 구조와 해석을 `Scenario Model`로 컴파일하고, 모험 시작 이후에는 현재 `Game State`, 이미 확정된 `Runtime-added Fact`, 관련 `Scenario Model` 정보, 잠긴 Storybook RAG를 이용해 현재 `Situation`을 구성한다. 실제 Story는 미리 저장된 Stage 순서가 아니라 `Situation → Player Action → GM Turn Resolution → 상태 변화`가 누적되며 만들어진다.

Storybook의 모든 사실을 사전에 구조화하는 것은 목표가 아니다. 런타임은 잠긴 Storybook RAG에서 필요한 source fact를 조회할 수 있고, source에 필요한 사실이 없거나 조회가 실패해 플레이가 막힐 경우에는 최소한의 `Runtime-added Fact`를 생성해 플레이 연속성을 보장한다.

## 2. Goals and Desired Outcomes

- **G-1** 기존 `Adventure Story Plan`의 Stage/branch/ending 진행 모델을 제거하고, 현재 세계 상태에서 파생되는 `Situation` 기반 런타임으로 대체한다.
- **G-2** Scenario Compilation은 플레이에 필요한 핵심 구조와 충돌 해석만 준비하고, Storybook의 모든 세부 정보를 사전에 채우도록 강제하지 않는다.
- **G-3** Storybook source 충실도를 유지하되, 이미 플레이에서 확정된 사실과 플레이 연속성을 더 우선한다.
- **G-4** 다중 Storybook의 충돌을 대부분 자동 통합하고, 사용자 개입이 필요한 경우에만 구체적인 BLOCKED 진단과 수정 제안을 제공한다.
- **G-5** 컴파일 시 source에 없는 사실의 생성 범위를 사용자가 `Creativity`로 통제할 수 있게 한다.
- **G-6** 런타임에서 필요한 scenario fact는 source 조회를 먼저 수행하고, source가 답을 주지 못할 때만 최소한의 `Runtime-added Fact`를 생성한다.
- **G-7** 숨겨진 정보는 GM 내부 추론에 사용할 수 있지만, 관찰·행동·대화 등을 통해 정당하게 공개되기 전에는 player-visible output에 노출하지 않는다.
- **G-8** 하나의 GM Turn에서 상태 변경, 새 Runtime-added Fact, 플레이어 결과를 원자적으로 확정한다.
- **G-9** 핵심 resolution condition이 충족되면 자연스러운 concluding Scene을 제공한 뒤 모험을 완료한다.

## 3. Users and Actors

- **Solo Player**: Storybook 구성을 준비하고, 필요한 경우 Primary Storybook, Integration Prompt, Creativity를 선택하며, 모험 시작 후 행동을 선언하고 GM 결과를 받는 유일한 인간 플레이어다.
- **AI Game Master**: 현재 Situation과 관련 정보로 행동을 판정하고 Scene을 서술하며, hidden information 공개 규칙을 지킨다.
- **Scenario Compilation**: Storybook들을 통합해 숨겨진 Scenario Model과 diagnostics를 만드는 준비 과정이다.
- **Storybook Source / RAG**: 모험 시작 시 잠기는 원본 scenario source와 해당 source의 검색 수단이다.
- **Rulebook / Game System**: 일반 게임 규칙, stat, 판정 의미를 제공한다. Scenario Model은 일반 규칙을 scenario fact로 중복 저장하지 않는다.

## 4. Ubiquitous Language and Terminology

- **Scenario Source**: 한 모험에 사용하는 Storybook, Rulebook, map/asset 등 원본 자료 집합.
- **Storybook**: 시나리오 내용이 담긴 source document. 새 흐름에서는 Storybook이 최소 1개 필요하다.
- **Primary Storybook**: Storybook이 여러 개일 때 기본 충돌 우선권을 갖는 하나의 Storybook. Storybook이 하나면 자동으로 Primary가 된다.
- **Supplement Storybook**: Primary가 아닌 Storybook. Primary에 없는 정보나 호환되는 정보를 보충한다.
- **Integration Prompt**: 사용자가 Storybook 간 충돌을 어떻게 해석·통합할지 지정하는 선택 입력. 충돌 해석에서 Primary보다 높은 권한을 가지지만, 일반적인 자유 재작성 지시로 사용하지 않는다.
- **Creativity**: Scenario Compilation이 source에 없는 핵심 필요 정보를 얼마나 보완할 수 있는지 정하는 전역 설정. `NONE`, `CONSERVATIVE`, `CREATIVE`가 있으며 기본값은 `CONSERVATIVE`다.
- **Scenario Compilation**: 잠긴 runtime source로 사용하기 전에 Storybook을 통합·검증해 Scenario Model을 만드는 준비 과정.
- **Scenario Model**: 모험 시작 전에 만들어지는 숨겨진 컴파일 결과. actor/location/relationship, objective와 resolution condition, revelation/clue, scenario-specific encounter anchor, conflict interpretation 등 런타임에 필요한 핵심 구조를 담을 수 있다. 모든 source fact를 복제하는 저장소는 아니다.
- **Game State**: 실제 플레이 결과로 변한 현재 세계 상태. 문 파손, 아이템 이동, NPC 상태 변화, 플레이어가 만든 함정처럼 행동의 지속적 결과를 포함한다.
- **Runtime-added Fact**: 런타임에서 필요한 scenario fact를 Storybook RAG에서 찾지 못했거나 조회가 실패했을 때 플레이 연속성을 위해 생성한 새로운 지속적 사실. Scenario Model에 다시 기록하지 않고 해당 playthrough의 canon으로 유지한다.
- **Situation**: `Scenario Model + relevant source/RAG + Runtime-added Facts + current Game State + recent play`에서 파생되는 숨겨진 GM용 현재 문제 상태. 정본 source 자체가 아니며 여러 GM Turn 동안 유지될 수 있다.
- **Scene**: Situation을 플레이어에게 보여 주는 narration, dialogue, observable reaction 등의 표현.
- **GM Turn**: 하나의 Solo Player 입력 또는 확정된 상호작용에 대해 AI GM이 판정, narration, 상태 변화, 다음 Situation 판단까지 수행하는 원자적 진행 단위.
- **Revelation**: 플레이어가 발견할 수 있는 중요한 진실.
- **Clue**: Revelation을 발견하도록 이어지는 관찰 가능한 증거 또는 전달 경로.
- **Resolution Condition**: 모험의 핵심 문제가 해결된 것으로 판단할 수 있는 조건.
- **Story**: 실제 플레이에서 완료된 Situation과 행동, 해결 결과가 누적된 기록. 사전 계획된 미래 순서가 아니다.
- **Plot**: 미리 결정된 미래 사건 순서. 새 런타임의 핵심 실행 모델로 사용하지 않는다.

`Playability-required Fact`, `Player Notes`, 사용자용 `Player Knowledge` 기능은 이 Product Spec의 도메인 용어로 사용하지 않는다.

## 5. Core Use Cases

### UC-1 Scenario를 컴파일한다

Solo Player는 Storybook을 1개 이상 준비한다. Storybook이 하나면 자동으로 Primary가 되고, 2개 이상이면 사용자가 Primary를 명시한다. 사용자는 선택적으로 Integration Prompt를 입력하고 Creativity를 선택한다.

Scenario Compilation은 source를 통합해 숨겨진 Scenario Model을 만든다. Storybook 충돌은 가능한 한 자동으로 해결하며, Integration Prompt가 있으면 해당 지시가 충돌 해석에서 가장 높은 우선순위를 가진다. Source에 정보가 없다는 이유만으로 모든 빈칸을 채우지 않는다. 그 정보가 없으면 핵심 목표를 진행하거나 해결할 수 없는 경우에만 Creativity 정책에 따라 보완한다.

결과는 `READY` 또는 예외적인 `BLOCKED`다.

### UC-2 모험 시작 전에 Scenario를 재컴파일한다

모험 시작 전에는 Primary Storybook, Integration Prompt, Creativity를 변경할 수 있다. 값이 변경되면 Scenario Compilation을 다시 수행하고 이전 candidate는 대체된다. 사용자가 별도로 Scenario Model 내용을 검토하거나 승인하는 단계는 없다.

### UC-3 모험을 시작하고 첫 Situation을 만든다

현재 Scenario Model이 `READY`일 때 모험을 시작할 수 있다. Adventure Start가 성공하면 해당 Scenario Model과 Storybook source/RAG 범위를 잠근다. 이후 Scenario Model은 재컴파일·교체·런타임 enrichment할 수 없다.

첫 Situation은 compilation 결과로 미리 저장하지 않는다. Adventure Start 시 초기 Game State와 관련 Scenario Model 정보 및 Storybook RAG를 사용해 런타임에서 첫 Situation을 구성하고 첫 Scene을 출력한다.

### UC-4 현재 Situation에서 GM Turn을 진행한다

Solo Player가 행동을 선언하면 AI GM은 현재 플레이에서 이미 확정된 정보를 먼저 사용하고 부족한 정보만 source에서 찾는다.

scenario fact 조회 순서는 다음과 같다.

1. 현재 Game State
2. 기존 Runtime-added Facts
3. Scenario Model의 확정된 구조와 conflict interpretation
4. 잠긴 Storybook RAG
5. 그래도 없으면 최소 fallback fact 생성

AI GM은 관련 정보만 사용해 Turn Resolution을 만들고 player-visible narration을 생성한다. 한 Situation은 여러 GM Turn 동안 유지될 수 있다.

### UC-5 RAG fallback으로 Runtime-added Fact를 만든다

현재 판정 또는 진행에 필요한 scenario fact가 앞선 정보에 없으면 잠긴 Storybook RAG를 조회한다. RAG에서 source 답을 찾지 못하거나 조회 자체가 실패하면 플레이를 중단하지 않고 필요한 최소 사실을 생성한다.

생성된 사실은 별도의 중요도 판정 없이 Runtime-added Fact 후보가 된다. 새 사실이 기존 Runtime-added Fact 또는 Game State와 충돌하면 기존 플레이 사실을 유지하고 새 fact를 다시 생성한다. Turn이 성공적으로 commit될 때 Runtime-added Fact도 함께 확정된다.

### UC-6 현재 문제 상태가 바뀌면 Situation을 전환한다

GM은 현재 위치, conflict, threat, objective 또는 기타 문제 상태가 의미 있게 변해 기존 Situation이 현재 상태를 더 이상 대표하지 못할 때 새 Situation을 구성한다. 변화가 충분하지 않으면 기존 Situation을 유지한다.

새 Situation은 최신 Game State, Runtime-added Facts, 관련 Scenario Model slice, 필요한 Storybook RAG와 recent play를 기준으로 재구성한다.

### UC-7 hidden information을 안전하게 서술한다

GM은 hidden fact를 내부 reasoning과 observable reaction의 원인으로 사용할 수 있다. 하지만 player-visible output에는 현재 관찰 가능한 정보, 이전 플레이에서 이미 공개된 정보, 현재 행동/판정을 통해 정당하게 새로 공개되는 정보만 포함한다.

player-visible narration에서 hidden information 누출이 감지되면 이미 확정한 Turn Resolution은 유지하고 narration만 버린 뒤 재생성한다. narration 재생성이 반복 실패하면 unsafe output을 노출하거나 해당 Turn을 commit하지 않고 일반적인 retry failure를 보여 준다.

### UC-8 resolution condition을 충족해 모험을 완료한다

GM Turn 결과로 핵심 resolution condition이 충족되면 해당 행동의 결과와 자연스러운 concluding Scene을 생성한다. 그 Turn이 성공적으로 commit된 후 Adventure는 `COMPLETED`가 된다.

완료 이후의 자유 플레이 또는 post-adventure mode는 이번 범위에 포함하지 않는다.

## 6. Business Rules and Invariants

- **BR-1** 새 Scenario Model 흐름에는 Storybook이 최소 1개 필요하다. Rulebook-only adventure는 지원하지 않는다.
- **BR-2** `Adventure Story Plan`, Stage progression, 사전 branch/ending 순서는 새 런타임의 실행 모델로 사용하지 않는다.
- **BR-3** Scenario Model은 hidden compilation output이며 Solo Player가 내용을 직접 검토·승인하지 않는다.
- **BR-4** Scenario Compilation은 `UNCOMPILED → COMPILING → READY`를 기본 성공 흐름으로 하며, 자동 해결이 불가능한 예외 상황은 `BLOCKED`로 표시한다.
- **BR-5** Adventure Start는 현재 `READY` Scenario Model과 Storybook source 범위를 잠근다. 시작 후 재컴파일, 교체, enrichment는 허용하지 않는다.
- **BR-6** Source에 없는 정보는 기본적으로 unspecified 상태로 둔다. 그 정보가 없으면 시나리오의 핵심 목표를 진행하거나 해결할 수 없는 경우에만 compilation-time supplementation 대상이 된다.
- **BR-7** `Creativity = NONE`이면 source에 없는 핵심 필요 사실을 생성하지 않는다. 필요한 사실이 없으면 compilation은 BLOCKED가 된다.
- **BR-8** `Creativity = CONSERVATIVE`이면 Storybook과 강하게 연결되거나 암시된 최소 정보만 보완하며 setting을 확장하지 않는다.
- **BR-9** `Creativity = CREATIVE`이면 Storybook과 모순되지 않는 범위에서 핵심 시나리오를 실행·해결하기 위해 필요한 새로운 사실을 만들 수 있다. 선택적 world-building을 위한 생성은 허용하지 않는다.
- **BR-10** Creativity 기본값은 `CONSERVATIVE`이며 자동 escalation하지 않는다. 현재 단계로 해결할 수 없으면 사용자가 직접 높은 단계로 변경하고 재컴파일한다.
- **BR-11** Creativity는 missing core-needed information을 보완하는 정책이며 Storybook 간 conflict resolution 정책과 별개다.
- **BR-12** 다중 Storybook conflict authority는 `User Integration Prompt > Primary Storybook > Supplement Storybooks` 순이다. Supplements는 Primary에 없는 정보나 호환되는 정보를 보충한다.
- **BR-13** Storybook 충돌을 해소하기 위해 source A와 B 어디에도 없는 제3의 사실을 임의로 발명하지 않는다.
- **BR-14** Integration Prompt는 Storybook 간 충돌의 해석·명확화에만 최고 권한을 가지며 scenario를 자유롭게 다시 쓰는 일반 authoring prompt가 아니다.
- **BR-15** 자동으로 처리 가능한 conflict와 불완전성은 가능한 한 자동 통합한다. BLOCKED는 사용자 개입 없이는 안전하게 진행할 수 없는 최후 수단이다.
- **BR-16** BLOCKED diagnostics는 spoiler를 숨기지 않는다. 실제 문제, 관련 document/source 위치, 충돌 또는 누락된 fact, 자동 해결 실패 이유, 현재 설정을 보여 주고 구체적인 수정 방법과 적용 가능한 Integration Prompt 예시를 제안한다.
- **BR-17** Runtime RAG는 compilation-time conflict interpretation을 다시 열거나 뒤집을 수 없다.
- **BR-18** Runtime에서 source fact가 필요하면 `Game State → Runtime-added Facts → Scenario Model의 확정 구조/해석 → Storybook RAG → fallback generation` 순으로 찾는다.
- **BR-19** Storybook RAG에서 찾은 source fact는 기존 compilation conflict interpretation 또는 이미 확정된 플레이 사실과 충돌하지 않는 한 현재 playthrough에서 사용할 수 있다.
- **BR-20** RAG fallback으로 생성된 사실은 그 생성 이유 자체가 이후 일관된 참조가 필요한 scenario fact이므로, 성공한 Turn에서 모두 Runtime-added Fact로 저장한다. 별도 중요도/지속성 판정은 하지 않는다.
- **BR-21** Runtime-added Fact는 locked Scenario Model에 write-back하지 않는다.
- **BR-22** Runtime-added Fact 생성은 compilation Creativity의 영향을 받지 않는다. `Creativity = NONE`으로 컴파일된 모험도 런타임에서 플레이 지속에 필요하면 최소 fallback fact를 만들 수 있다.
- **BR-23** 새 Runtime-added Fact가 기존 Runtime-added Fact나 Game State와 충돌하면 기존 플레이 사실이 우선하며 새 fact를 다시 생성한다.
- **BR-24** 이미 확정된 Runtime-added Fact와 나중에 검색된 Storybook source가 충돌하면 해당 playthrough에서는 Runtime-added Fact를 유지하며 retcon하지 않는다.
- **BR-25** Game State는 Scenario Model에 미리 선언된 필드로 제한되지 않는다. 실제 플레이 행동이 만든 새로운 지속적 상태를 기록할 수 있다.
- **BR-26** Runtime-added Fact는 source에 없어서 GM이 보완한 scenario/setting fact이고, 그 사실을 바탕으로 이후 플레이에서 일어난 변화는 Game State로 기록한다.
- **BR-27** Situation은 정본 source가 아니라 숨겨진 runtime context이며 여러 GM Turn 동안 유지할 수 있다.
- **BR-28** Situation 전환은 미리 정한 Stage 번호가 아니라 현재 문제 상태가 의미 있게 바뀌었는지를 기준으로 GM이 자동 판단한다.
- **BR-29** 첫 Situation도 compilation에 저장하지 않고 Adventure Start 시 runtime에서 파생한다.
- **BR-30** 일반 D&D mechanics/stat/rule semantics는 Rulebook/Game System의 책임이며 Scenario Model은 scenario-specific encounter facts만 보유한다.
- **BR-31** hidden information은 내부 reasoning에 사용할 수 있지만 player-visible output은 관찰 가능, 이미 공개됨, 현재 행동으로 정당하게 공개됨 중 하나를 만족해야 한다.
- **BR-32** spoiler filtering 때문에 narration을 재생성할 때 Turn Resolution, dice result, world-state decision을 다시 굴리거나 바꾸지 않는다.
- **BR-33** GM Turn은 `Game State 변경 + 새 Runtime-added Facts + player-visible result`를 하나의 atomic commit으로 확정한다.
- **BR-34** Turn이 실패하거나 폐기되면 그 Turn에서 staging된 Game State 변경과 Runtime-added Facts, player-visible result를 모두 폐기한다.
- **BR-35** 핵심 resolution condition이 충족된 Turn은 concluding Scene까지 성공적으로 생성·commit한 뒤 Adventure를 `COMPLETED`로 전환한다.

## 7. States and State Transitions

### Scenario Compilation

- `UNCOMPILED`: 아직 유효한 Scenario Model이 없음.
- `COMPILING`: 현재 Storybook selection, Primary, Integration Prompt, Creativity로 Scenario Model을 생성·검증 중.
- `READY`: Adventure Start에 필요한 핵심 구조, resolution condition, 시작 정보와 locked-source 범위가 준비됨.
- `BLOCKED`: 자동 통합과 현재 Creativity로도 사용자 결정 없이는 compilation을 완료할 수 없음.

전이:

- `UNCOMPILED → COMPILING`: compilation 요청.
- `COMPILING → READY`: 시작 가능한 Scenario Model 검증 성공.
- `COMPILING → BLOCKED`: 최후의 자동 해결 이후에도 필요한 문제 잔존.
- `READY/BLOCKED → COMPILING`: Adventure Start 전 Primary, Integration Prompt, Creativity 또는 source 구성을 변경하고 재컴파일.
- `READY → Adventure Start`: current READY model과 source를 lock. 이후 compilation lifecycle로 돌아가지 않는다.

`READY`는 모든 세부 fact가 사전에 채워졌다는 의미가 아니다. 모험을 시작할 수 있고 이후를 Scenario Model, Storybook RAG, Runtime-added Facts, Game State로 이어갈 수 있으면 충분하다.

### Runtime-added Fact

- Turn 처리 중 필요한 사실이 source에서 발견되지 않으면 candidate가 생성된다.
- Turn 성공 시 candidate가 Runtime-added Fact로 commit되어 해당 playthrough에서 계속 유지된다.
- Turn 실패/폐기 시 candidate도 폐기된다.
- Commit 후 직접 retcon/overwrite하지 않는다. 그 사실과 관련된 이후 세계 변화는 Game State로 표현한다.

### Situation

Situation은 명시적인 Stage 상태 머신이 아니다. 생성된 Situation은 현재 문제 상태를 계속 대표하는 동안 유지되고, 위치/conflict/threat/objective 등 effective world state가 의미 있게 달라지면 최신 상태에서 새 Situation으로 교체된다.

### Adventure

- `ACTIVE`: Adventure Start와 첫 Situation 생성 이후 정상 플레이 중.
- `COMPLETED`: resolution condition을 충족한 Turn에서 concluding Scene까지 commit한 상태.

`COMPLETED` 이후 post-adventure 자유 플레이 전이는 이번 범위에 없다.

## 8. Failures, Exceptions, and Boundary Conditions

- Storybook이 없으면 Scenario Compilation을 READY로 만들거나 Adventure를 시작할 수 없다.
- Storybook이 2개 이상인데 Primary가 정해지지 않으면 compilation 입력이 완전하지 않다.
- `Creativity = NONE`에서 핵심 진행/해결에 반드시 필요한 source-missing fact가 있으면 BLOCKED가 된다.
- Storybook conflict는 우선순위와 Integration Prompt로 최대한 자동 해결한다. 정말 해석이 불가능하면 BLOCKED diagnostics에 문제와 source 위치, 이유, 구체적인 수정 제안을 노출한다.
- Runtime RAG가 결과를 찾지 못하거나 retrieval 자체가 실패해도 단순한 source 부족 때문에 플레이를 중단하지 않는다. 현재 Turn에 필요한 최소 Runtime-added Fact를 생성한다.
- 새 fallback fact가 기존 플레이 사실과 충돌하면 기존 사실을 바꾸지 않고 fallback을 다시 생성한다.
- 이미 Runtime-added Fact가 commit된 뒤 source에서 반대 사실을 발견해도 해당 playthrough를 retcon하지 않는다.
- player-visible narration에서 hidden information 누출을 발견하면 unsafe narration을 폐기하고 동일한 Turn Resolution로 narration만 재생성한다.
- safe narration을 반복 생성하지 못하면 unsafe output과 상태를 commit하지 않고 일반적인 retry failure를 반환한다.
- GM Turn 처리 도중 어떤 단계에서든 Turn 전체를 완료하지 못하면 해당 Turn의 state/fact/output을 부분적으로 남기지 않는다.
- Scenario Model이 잠긴 이후 source 구성이나 compilation setting을 바꾸는 것은 허용하지 않는다.
- Adventure가 `COMPLETED`가 된 후 같은 모험에서 자유 플레이를 계속하는 동작은 지원하지 않는다.

## 9. Inputs and Outputs

### Scenario Compilation Inputs

필수:
- Storybook 1개 이상
- Storybook이 2개 이상일 때 Primary Storybook

선택:
- Integration Prompt
- Creativity (`NONE | CONSERVATIVE | CREATIVE`, default `CONSERVATIVE`)
- Scenario Source에 포함되는 관련 Rulebook/map/asset 등의 자료

### Scenario Compilation Outputs

내부:
- hidden Scenario Model
- source/conflict interpretation
- 시작 Situation을 runtime에서 만들기 위한 start information
- core objective와 resolution condition

사용자에게 보이는 결과:
- compilation 상태 (`READY` 또는 `BLOCKED`)
- warnings/diagnostics
- BLOCKED일 경우 실제 문제와 source 위치, 자동 해결 실패 이유, 수정 제안

### Runtime Inputs

- locked Scenario Model의 relevant slice
- current Game State
- committed Runtime-added Facts
- recent play
- locked Storybook RAG 결과
- 필요한 Rulebook/Game System 정보
- 현재 Solo Player action

전체 Scenario Model 또는 Storybook 전체를 매 GM Turn의 입력으로 전달해야 한다는 요구사항은 없다. 필요한 관련 정보만 사용하면 된다.

### Runtime Outputs

내부:
- current/next Situation
- Turn Resolution
- Game State changes
- 새 Runtime-added Fact candidates 및 commit 결과
- resolution condition 평가

플레이어:
- 현재 관찰 가능하거나 정당하게 공개된 Scene/narration
- 행동의 observable result
- 완료 시 concluding Scene

## 10. Scope and Non-goals

### In Scope

- 기존 Adventure Story Plan 흐름의 완전 대체
- Storybook 필수 Scenario Compilation
- 다중 Storybook Primary/Supplement 통합과 Integration Prompt
- compilation Creativity 정책
- hidden Scenario Model과 Adventure Start lock
- Runtime RAG source retrieval
- Runtime-added Fact fallback과 playthrough continuity
- Game State와 Runtime-added Fact의 구분
- Situation 기반 multi-turn runtime 진행
- hidden information / spoiler-safe player output
- atomic GM Turn
- resolution condition 기반 Adventure completion

### Non-goals

- Rulebook-only adventure 생성 또는 진행
- Stage/branch/ending을 미리 계획하는 Adventure Story Plan 유지
- Scenario Model을 Solo Player가 직접 열람·편집·승인하는 기능
- `Player Notes` 기능
- 사용자-facing `Player Knowledge` 관리 기능
- source의 모든 세부 fact를 Scenario Model schema에 강제로 채우는 것
- 선택적 world-building을 위한 compilation creativity
- provenance (`SOURCE`/`GENERATED`)를 Scenario Model의 필수 domain concept로 도입하는 것
- post-adventure 자유 플레이
- 구체적인 persistence schema, service/module 배치, API shape, framework 선택 등 Architecture 결정
- Scenario Model의 최종 물리 schema를 Product Spec에서 확정하는 것

## 11. Priorities and Trade-offs

### Runtime priority

충돌 시 다음 원칙을 우선한다.

1. **플레이 연속성**
2. **이미 확정된 플레이의 일관성**
3. **Scenario Compilation에서 확정된 source/conflict interpretation**
4. **나중에 검색된 원본 Storybook source 충실도**
5. **임의 창작**

단, 이 우선순위는 source를 무시하고 바로 창작한다는 뜻이 아니다. 새 persistent fact가 필요할 때는 **항상 Storybook RAG lookup을 먼저 시도**하고, source가 답을 주지 못할 때만 최소 fallback fact를 생성한다.

### Compilation authority

Storybook 충돌 해석의 우선순위는 다음과 같다.

`User Integration Prompt > Primary Storybook > Supplement Storybooks`

Creativity는 이 충돌 우선순위 다음 단계가 아니라 별도의 missing-information policy다. 충돌 해결을 위해 임의의 제3 fact를 생성하지 않는다.

### Product trade-offs

- 모든 정보를 미리 구조화하는 완전성보다 실제 플레이를 시작하고 계속할 수 있는 준비 상태를 우선한다.
- late-discovered source fidelity보다 이미 플레이어가 경험한 세계의 연속성을 우선한다.
- spoiler를 감춘 diagnostics보다 사용자가 실제로 BLOCKED 문제를 해결할 수 있는 구체적 diagnostics를 우선한다.
- 서술 재생성 편의보다 Turn Resolution의 안정성과 non-reroll invariant를 우선한다.
- preplanned plot control보다 플레이 결과에서 Story가 형성되는 emergent runtime을 우선한다.

## 12. Success Conditions and Acceptance Criteria

- **AC-1** Storybook이 0개인 source 구성은 새 Scenario Compilation에서 READY가 될 수 없다.
- **AC-2** Storybook이 정확히 1개면 해당 Storybook이 자동으로 Primary가 된다.
- **AC-3** Storybook이 2개 이상이면 Solo Player가 Primary를 지정한 뒤 compilation을 실행할 수 있다.
- **AC-4** 동일 conflict에 Integration Prompt와 Primary가 서로 다른 해석을 요구하면 Integration Prompt의 충돌 해석이 적용된다.
- **AC-5** Creativity를 지정하지 않으면 `CONSERVATIVE`가 적용된다.
- **AC-6** `NONE`에서 핵심 목표 진행 또는 해결에 반드시 필요한 source-missing fact가 발견되면 compilation은 BLOCKED가 된다.
- **AC-7** `CONSERVATIVE`는 core progress/resolution에 필요하지 않은 설정을 새로 만들지 않는다.
- **AC-8** `CREATIVE`도 core progress/resolution에 필요하지 않은 optional world-building을 위해 fact를 생성하지 않는다.
- **AC-9** 사소한 NPC/장소 세부 정보가 source에 없다는 이유만으로 READY가 차단되지 않는다.
- **AC-10** BLOCKED 결과는 실제 문제, source 위치, 자동 해결 실패 이유와 최소 1개의 구체적인 수정 제안을 제공한다.
- **AC-11** Adventure Start 전 Primary, Integration Prompt 또는 Creativity를 바꾸면 새 compilation 결과가 이전 candidate를 대체한다.
- **AC-12** `READY`가 아닌 Scenario Model로 Adventure Start를 완료할 수 없다.
- **AC-13** Adventure Start가 성공하면 해당 Scenario Model과 Storybook source 범위가 잠기고 이후 재컴파일/교체되지 않는다.
- **AC-14** 첫 Situation은 Scenario Compilation에 미리 저장된 Stage가 아니라 Adventure Start 시 current state와 source에서 생성된다.
- **AC-15** runtime progression은 Stage position 또는 preplanned branch/ending 선택을 필수 실행 단위로 사용하지 않는다.
- **AC-16** runtime에서 scenario fact가 필요할 때 `Game State → Runtime-added Facts → Scenario Model → Storybook RAG → fallback` 순으로 부족한 정보를 찾는다.
- **AC-17** RAG가 source answer를 찾지 못하거나 retrieval이 실패하면 현재 플레이를 진행하기 위한 최소 fact를 생성하고, Turn 성공 시 Runtime-added Fact로 commit한다.
- **AC-18** 새 fallback fact가 기존 Runtime-added Fact 또는 Game State와 충돌하면 기존 사실을 유지하고 새 fact를 다시 생성한다.
- **AC-19** commit된 Runtime-added Fact와 이후 검색된 Storybook fact가 충돌해도 이미 진행된 playthrough를 retcon하지 않는다.
- **AC-20** `Creativity = NONE`인 모험에서도 runtime source lookup 실패 때문에 진행이 막힐 경우 최소 Runtime-added Fact fallback을 사용할 수 있다.
- **AC-21** Runtime-added Fact는 생성됐다는 이유만으로 플레이어에게 공개되지 않으며 Storybook hidden fact와 동일한 공개 규칙을 적용한다.
- **AC-22** hidden information leak이 발견된 narration 재생성은 이미 결정된 Turn Resolution, dice result, Game State decision을 변경하지 않는다.
- **AC-23** GM Turn이 성공하면 Game State changes, 새 Runtime-added Facts, player-visible result가 함께 commit되고, Turn 실패 시 셋 모두 commit되지 않는다.
- **AC-24** 현재 Situation이 더 이상 current problem state를 대표하지 못하면 최신 world state에서 새 Situation을 재구성한다.
- **AC-25** resolution condition이 충족된 Turn은 concluding Scene까지 성공적으로 제공한 뒤 Adventure를 `COMPLETED`로 전환한다.
- **AC-26** `COMPLETED` 이후 같은 Adventure의 post-adventure 자유 플레이는 제공하지 않는다.
- **AC-27** Scenario Model 내용을 Solo Player가 승인해야만 진행되는 별도 approval gate가 존재하지 않는다.
- **AC-28** `Player Notes`는 이번 기능의 사용자 기능으로 제공하지 않는다.
- **AC-29** generic rule/stat semantics는 Scenario Model의 scenario truth로 중복 정의하지 않고 Rulebook/Game System에서 참조한다.
- **AC-30** Product flow에서 `Adventure Story Plan`의 Stage/branch/ending preplanning이 제거되고, 실제 Story는 committed runtime history로만 형성된다.

## Product 다이어그램 계약

- Use Case Diagram: [UC-SMR.usecase.svg](diagrams/product/UC-SMR.usecase.svg)
- Activity Diagram: [UC-SMR.activity.svg](diagrams/product/UC-SMR.activity.svg)
- Editable originals:
  - `diagrams/product/UC-SMR.usecase.puml`
  - `diagrams/product/UC-SMR.activity.puml`
- Business-state diagram: **해당 없음 — Scenario Compilation과 Adventure/Situation의 업무 상태는 위 activity diagram과 본문의 state-transition 정의로 충분히 검토할 수 있으며, 별도 독립 업무 상태 검토 목적이 없다.**
- Product 단계에서는 class diagram을 생성하지 않는다.
