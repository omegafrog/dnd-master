# Product Spec — Scenario Model Runtime

Ticket ID: `scenario-model-runtime`

## 1. Problem and Context

현재의 `Adventure Story Plan`은 시작부터 결말까지 메인 줄기, 분기, 결말, 단계별 전환을 사전에 계획하고 Runtime GM이 그 계획 안에서 장면을 확장하는 방식이다. 이번 변경은 이 실행 모델을 완전히 대체한다. 실제 플레이의 선택과 결과가 이야기의 순서를 만들어야 하며, GM은 미리 정해진 Stage를 따라가는 대신 현재 세계 상태에서 `Situation`을 파생해 진행해야 한다.

Storybook 전체를 매 턴 GM 문맥에 싣거나 모든 세부 사실을 Scenario Model에 구조화하는 방식도 피해야 한다. Scenario Compilation은 핵심 구조와 해석 결정을 준비하고, Runtime은 현재 상황에 필요한 Storybook/Rulebook 근거를 RAG로 조회한다. Source에 없는 정보 때문에 플레이가 끊기는 경우에는 원작 충실성보다 플레이 연속성을 우선하여 필요한 최소 사실을 생성하고 이후 플레이에서 일관되게 유지한다.

이 Product Spec은 기존 `Adventure Story Plan → Stage progression → GM Elaboration` 흐름을 `Scenario Compilation → Scenario Model + RAG + Game State → Situation → GM Turn` 흐름으로 교체한다. 기존의 Rulebook-Only 모험 생성 흐름은 이 새 흐름에서 지원하지 않는다.

## 2. Goals and Desired Outcomes

- **G-SMR-01 — Emergent play:** 이야기 순서를 미리 계획하지 않고 실제 플레이의 Situation, 행동, 판정, 상태 변화의 누적으로 만든다.
- **G-SMR-02 — Source-grounded GM:** Storybook과 Rulebook의 원문 근거를 적극적으로 사용하되, Scenario Model에 원문 전체를 복제하지 않는다.
- **G-SMR-03 — Continuity first:** Runtime에서 필요한 source fact를 찾지 못하더라도 가능한 한 플레이를 중단하지 않고 일관된 fallback fact를 생성한다.
- **G-SMR-04 — Stable canon during play:** 모험 시작 후 컴파일 결과와 이미 확정된 플레이 사실을 retcon하지 않는다.
- **G-SMR-05 — Hidden-information safety:** GM의 내부 지식은 플레이어가 정당하게 알 수 있는 시점까지 player-visible output에 노출하지 않는다.
- **G-SMR-06 — Low-friction preparation:** AI가 Storybook 간 충돌과 누락을 최대한 자동으로 보정하며, 사용자 개입이 필요한 `BLOCKED`는 최후의 수단으로 취급한다.
- **G-SMR-07 — Scalable context use:** Runtime GM은 전체 시나리오를 항상 들고 있지 않고 현재 Situation에 필요한 구조와 source evidence만 사용한다.

## 3. Users and Actors

| Actor | Role |
|---|---|
| **Solo Player** | Storybook을 선택하고, 다중 Storybook이면 Primary를 지정하며, 필요 시 Integration Prompt와 Compilation Creativity를 설정한다. 모험 시작 후 유일한 인간 플레이어로 행동을 입력한다. |
| **AI Game Master** | 현재 Situation에서 장면 서술, 행동 판정, source 조회, fallback fact 생성, Situation 전환, concluding Scene 생성을 수행한다. 플레이어에게 공개하면 안 되는 내부 정보를 구분한다. |
| **Scenario Compilation** | 모험 시작 전에 Storybook들을 통합하고 핵심 구조, resolution, conflict interpretation을 준비하여 hidden Scenario Model을 만든다. |
| **Published Rulebook source** | 일반 게임 규칙과 판정 근거를 제공한다. Scenario-specific fact와 구분된다. |

Solo Player는 Scenario Model의 실제 내용을 검토·승인하지 않는다. 사용자가 다루는 것은 Storybook 구성, Primary, Integration Prompt, Creativity 및 Compilation Diagnostics이다.

## 4. Ubiquitous Language and Terminology

| Term | Definition |
|---|---|
| **Storybook** | 모험의 시나리오 사실을 담은 source 문서. 새 흐름에서 최소 1개가 필수다. |
| **Primary Storybook** | Storybook이 2개 이상일 때 사용자가 지정하는 기본 충돌 우선 source. 하나뿐이면 자동으로 Primary가 된다. |
| **Supplement Storybook** | Primary 외의 Storybook. Primary와 양립 가능한 내용을 보충하며, 사용자 지시가 없는 직접 충돌에서는 Primary보다 우선하지 않는다. |
| **Integration Prompt** | 여러 Storybook 사이의 큰 모순을 사용자가 의도한 방식으로 해석·해결하기 위한 optional 지시. Storybook 간 충돌 해결 범위에서 최우선 권위를 가진다. 자유 개작용 프롬프트가 아니다. |
| **Compilation Creativity** | Scenario Compilation에서 source에 없지만 핵심 목표 진행·해결을 위해 반드시 필요한 정보를 보완할 수 있는 범위. `NONE`, `CONSERVATIVE`, `CREATIVE`의 3단계이며 기본값은 `CONSERVATIVE`다. Runtime fallback에는 적용되지 않는다. |
| **Scenario Compilation** | Storybook, Primary, Integration Prompt, Creativity를 바탕으로 hidden Scenario Model을 생성·검증하는 준비 과정. |
| **Scenario Model** | 모험 시작 전에 컴파일되는 hidden runtime control model. 핵심 구조, 주요 관계, objective/resolution, secret/revelation, scenario-specific encounter anchor, conflict decision, source reference를 담는다. Storybook 전체의 구조화 복사본이 아니다. |
| **RAG source retrieval** | Scenario Compilation과 Runtime GM이 동일하게 locked Storybook/Rulebook source에서 관련 근거를 찾는 방식. RAG 결과는 별도 권위가 아니라 원 source의 검색 표현이다. |
| **Runtime-added Fact** | Runtime에서 필요한 scenario fact를 기존 플레이 정보와 locked source에서 찾지 못했을 때 플레이 연속성을 위해 GM이 생성하고 해당 플레이스루에 확정하는 지속적 사실. 생성되면 항상 저장된다. |
| **Game State** | 실제 플레이의 행동·판정·세계 변화로 만들어진 현재 세계 상태. Scenario Model 또는 Runtime-added Fact가 정의한 초기/지속 사실이 플레이에 의해 어떻게 바뀌었는지를 나타낸다. |
| **Effective World State** | 현재 판단에 사용되는 세계 상태. Game State, Runtime-added Facts, Scenario Model의 확정 해석, locked source를 현재 플레이의 우선순위에 따라 결합한 관점이다. |
| **Situation** | 현재 location/conflict/threat/objective/problem state를 나타내는 hidden GM runtime context. 여러 GM Turn 동안 유지될 수 있으며 현재 세계 상태가 달라지면 새로 파생된다. |
| **Scene** | Situation을 플레이어에게 보여주는 서술, 대화, 관찰 가능한 반응과 결과. Situation 자체의 구조는 플레이어에게 노출하지 않는다. |
| **GM Turn** | Solo Player의 텍스트 입력 또는 확정된 맵 상호작용 하나를 판정·서술·상태 변화와 함께 처리하는 원자적 진행 단위. |
| **Resolution Condition** | 핵심 모험 문제가 해결되었는지 판정할 수 있는 조건. 충족된 턴에서 concluding Scene을 만든 뒤 Adventure가 `COMPLETED`가 된다. |
| **Adventure Start Lock** | `READY` Scenario Model과 source 구성을 모험 시작 순간 해당 플레이스루에 고정하는 규칙. 시작 후 Scenario Model을 재컴파일·교체하지 않는다. |

`Playability-required Fact`라는 별도 도메인 용어는 사용하지 않는다. Source에 없는 정보는 기본적으로 비워 두되, Scenario Compilation에서 핵심 목표를 진행하거나 해결할 수 없는 경우에만 Creativity 정책에 따라 보완한다.

## 5. Core Use Cases

### UC-SMR-00 — Scenario Model-driven Adventure Flow

새 Scenario Model 기반 모험 준비와 Runtime 실행 전체를 포괄하는 상위 흐름이다.

### UC-SMR-01 — Prepare and Compile Scenario

1. Solo Player는 공개된 Rulebook 하나와 Storybook 하나 이상을 선택한다.
2. Storybook이 하나면 자동으로 Primary가 된다. 둘 이상이면 Solo Player가 Primary를 명시적으로 선택한다.
3. 사용자는 optional Integration Prompt를 최초 컴파일 전부터 입력할 수 있다.
4. 사용자는 Compilation Creativity를 선택한다. 기본값은 `CONSERVATIVE`다.
5. Scenario Compilation은 source를 통합하고 가능한 충돌·누락을 자동으로 보정한다.
6. 시작 가능한 모델을 만들면 `READY`가 된다. 자동 보정으로도 불가능하면 `BLOCKED`가 되고 구체적 진단과 해결 제안을 보여준다.
7. 모험 시작 전에는 Primary, Integration Prompt, Creativity 또는 source 구성을 바꾸고 재컴파일할 수 있다. 새 컴파일은 이전 candidate를 대체한다.

### UC-SMR-02 — Start Adventure and Derive Initial Situation

1. `READY` 상태에서 Solo Player가 Adventure Start를 실행한다.
2. 현재 Scenario Model과 locked Storybook/Rulebook source 구성이 해당 플레이스루에 고정된다.
3. 초기 Game State를 만든다.
4. 첫 Situation은 컴파일 결과에 미리 저장하지 않고, 시작 시점의 Scenario Model + Game State + 관련 RAG evidence로 Runtime에서 파생한다.
5. 첫 player-visible Scene을 출력한다.

### UC-SMR-03 — Run Situation-based GM Turn

1. Solo Player가 행동을 입력하거나 확정된 맵 상호작용을 수행한다.
2. GM은 필요한 사실을 현재 플레이의 우선순위에 따라 조회한다.
3. Scenario Model에 구조화되지 않은 Storybook fact도 locked source RAG에서 명시적으로 찾으면 사용할 수 있다.
4. 필요한 scenario fact가 source에도 없거나 retrieval로 확보되지 않으면, 플레이를 멈추지 않고 최소한의 fallback fact를 생성하여 Runtime-added Fact 후보로 둔다.
5. 기존 Game State 또는 Runtime-added Fact와 충돌하는 fallback은 폐기하고 기존 플레이 기록에 맞게 다시 생성한다.
6. 판정 결과, Game State 변경, 새 Runtime-added Fact, player-visible result를 하나의 GM Turn으로 처리한다.
7. 현재 Situation이 여전히 현재 문제 상태를 표현하면 유지하고, 그렇지 않으면 최신 Effective World State에서 새 Situation을 파생한다.

### UC-SMR-04 — Protect Hidden Information

1. GM은 hidden Scenario Model/source/Runtime-added Fact를 내부 추론에 사용할 수 있다.
2. player-visible output에는 현재 관찰 가능한 정보, 이전에 정당하게 공개된 정보, 현재 행동·판정으로 새롭게 정당하게 드러난 정보만 포함한다.
3. 출력 검증에서 hidden information leak가 발견되면 이미 계산된 Turn Resolution은 유지하고 narration만 폐기·재생성한다.
4. 안전한 narration 생성이 반복적으로 실패하면 해당 턴은 commit하지 않고 generic retry 결과를 보여준다.

### UC-SMR-05 — Conclude Adventure

1. GM Turn의 결과로 핵심 Resolution Condition이 충족되었는지 판정한다.
2. 충족되면 해당 행동 결과와 자연스러운 concluding Scene을 생성한다.
3. concluding Scene과 턴 결과를 commit한 뒤 Adventure를 `COMPLETED`로 전환한다.
4. 이번 범위에서는 `COMPLETED` 이후 자유 플레이를 제공하지 않는다.

## 6. Business Rules and Invariants

| ID | Requirement / Invariant |
|---|---|
| **PR-SMR-001** | Storybook 하나 이상이 없으면 새 Scenario Model 모험을 컴파일·시작할 수 없다. Rulebook-Only 모험은 지원하지 않는다. |
| **PR-SMR-002** | Storybook이 2개 이상이면 사용자가 Primary Storybook을 명시적으로 지정해야 한다. 하나면 자동 지정한다. |
| **PR-SMR-003** | Integration Prompt는 Storybook 간 충돌 해석 범위에서 Primary보다 우선한다. 자유 개작이나 unrelated optional world-building의 권한으로 사용하지 않는다. |
| **PR-SMR-004** | Integration Prompt가 없는 direct source conflict는 Primary를 기본 우선하고 Supplement는 compatible/missing detail을 보충한다. Conflict 해결만을 위해 source에 없는 제3의 사실을 만들지 않는다. |
| **PR-SMR-005** | Compilation Creativity는 `NONE / CONSERVATIVE / CREATIVE`이며 기본 `CONSERVATIVE`다. 자동으로 더 높은 단계로 승격하지 않는다. `NONE`은 source 밖의 필수 보완을 허용하지 않고, `CONSERVATIVE`는 source에 강하게 연결된 최소 보완만, `CREATIVE`는 source와 모순되지 않는 범위에서 핵심 진행·해결에 필요한 새 사실을 허용한다. Optional world-building을 채우기 위해 사용하지 않는다. |
| **PR-SMR-006** | Scenario Compilation은 사용자가 개입하기 전에 source 통합, Primary 우선순위, Integration Prompt, Creativity를 사용해 가능한 한 `READY`까지 자동 보정해야 한다. `BLOCKED`는 최후의 수단이다. |
| **PR-SMR-007** | `BLOCKED`이면 사용자가 실제로 수정할 수 있도록 문제된 conflict/missing fact, 관련 source와 위치, 자동 처리 실패 이유를 공개하고 구체적 해결 방향 및 Integration Prompt 예시를 제안한다. 이 경우 스포일러보다 수정 가능성을 우선한다. |
| **PR-SMR-008** | Scenario Model은 사용자 검토 대상이 아닌 hidden 결과물이며, Adventure Start 시 lock된다. 시작 후 재컴파일·교체하지 않는다. |
| **PR-SMR-009** | Scenario Model은 Storybook 전체를 복제하지 않는다. 핵심 canonical structure, conflict interpretation, source reference를 보관하며, Runtime은 필요한 source detail을 RAG로 조회할 수 있어야 한다. |
| **PR-SMR-010** | Runtime RAG가 locked Storybook에서 찾은 명시적 source fact는 Scenario Model에 미리 구조화되지 않았더라도 사용할 수 있다. 단 이미 확정된 conflict interpretation 또는 현재 플레이 사실을 뒤집을 수 없다. RAG로 발견한 source fact를 Scenario Model에 다시 영구 적재하지 않는다. |
| **PR-SMR-011** | Runtime fact 조회 우선순위는 `Game State → 기존 Runtime-added Facts → Scenario Model의 확정 구조/해석 → locked Storybook RAG → fallback 생성`이다. 일반 게임 규칙은 selected Rulebook source에서 별도로 조회한다. |
| **PR-SMR-012** | Runtime에서 필요한 scenario fact가 앞선 단계에서 확보되지 않으면 플레이 연속성을 위해 fallback을 생성하고 항상 Runtime-added Fact로 저장한다. Compilation Creativity는 이 Runtime fallback을 제한하지 않는다. |
| **PR-SMR-013** | Commit된 Runtime-added Fact는 해당 플레이스루에서 source보다 우선하며 나중에 발견된 원문과 충돌해도 retcon하지 않는다. Runtime-added Fact 자체를 직접 덮어쓰지 않고 이후 세계의 변화는 Game State로 표현한다. |
| **PR-SMR-014** | 새 fallback이 기존 Runtime-added Fact 또는 현재 Game State와 충돌하면 기존 플레이 기록이 우선하며 새 fact를 다시 생성한다. |
| **PR-SMR-015** | Runtime-added Fact는 생성됐다는 이유로 플레이어에게 공개되지 않는다. Storybook의 secret과 동일한 visibility rule을 따른다. |
| **PR-SMR-016** | Situation은 hidden runtime context이고 하나의 Situation이 여러 GM Turn을 포함할 수 있다. Effective World State의 의미 있는 변화로 기존 Situation이 현재 location/conflict/threat/objective/problem state를 더 이상 나타내지 못할 때 새 Situation을 파생한다. |
| **PR-SMR-017** | Scenario Compilation은 initial Situation 자체를 저장하지 않는다. 첫 Situation은 Adventure Start 시 파생한다. |
| **PR-SMR-018** | 하나의 GM Turn에서 확정되는 Game State 변경, 새 Runtime-added Facts, player-visible 결과는 atomic하게 commit한다. Turn이 abort되면 해당 Turn의 신규 변경과 facts도 확정하지 않는다. |
| **PR-SMR-019** | Hidden-information leak를 고칠 때 Turn Resolution을 다시 계산하지 않는다. 같은 resolution을 사용하여 player-visible narration만 재생성하므로 spoiler safety가 주사위·판정·세계 상태를 재결정할 수 없다. |
| **PR-SMR-020** | Resolution Condition이 충족된 GM Turn에서 concluding Scene까지 생성·commit한 뒤 Adventure를 `COMPLETED`로 전환한다. |
| **PR-SMR-021** | Runtime source retrieval 실패나 source의 세부 누락만을 이유로 플레이를 중단하지 않는다. 필요한 scenario fact는 Runtime-added Fact fallback으로 이어간다. |
| **PR-SMR-022** | Scenario-specific encounter 사실(해당 장소의 적, 함정, hazard, scenario-defined reward/condition 등)은 scenario source/model 영역의 사실이며, 일반 stat block·공통 판정 mechanics는 Rulebook source의 규칙이다. |

## 7. States and State Transitions

### Scenario Compilation

| State | Meaning | Allowed transitions |
|---|---|---|
| `UNCOMPILED` | 아직 hidden Scenario Model candidate가 없음 | `COMPILING` |
| `COMPILING` | source 통합, 누락 보완, conflict resolution 및 startability 판단 중 | `READY`, `BLOCKED` |
| `READY` | 현재 구성으로 Adventure Start가 가능함 | `RUNNING`(start), `COMPILING`(시작 전 입력 변경 후 재컴파일) |
| `BLOCKED` | 자동 보정으로도 startable model을 만들지 못함 | `COMPILING`(source/Primary/prompt/creativity 변경 및 재시도) |

`READY`는 모든 세부 fact가 사전에 채워졌다는 뜻이 아니다. 최소 조건은 locked source가 준비되고, 핵심 구조와 시작 조건을 만들 수 있으며, 핵심 목표와 해결 여부를 판단할 수 있고, 시작을 막는 치명적 모순이 없는 것이다. 향후 세부 사실은 Runtime RAG와 Runtime-added Fact로 다룬다.

### Adventure

| State | Meaning | Transition |
|---|---|---|
| `READY` | 컴파일 완료, 아직 시작 전 | Adventure Start → `RUNNING` |
| `RUNNING` | Scenario Model/source가 lock되고 Situation 기반 GM Turn 진행 중 | 핵심 Resolution Condition 충족 + concluding Scene commit → `COMPLETED` |
| `COMPLETED` | 모험 완료 | 종료. post-adventure free play 없음 |

### Situation

Situation은 Stage 번호를 따라 진행하지 않는다. 현재 Effective World State에서 파생되며 여러 GM Turn 동안 유지될 수 있다. location, conflict, threat, objective 또는 핵심 problem state가 의미 있게 바뀌어 현재 Situation이 더 이상 적합하지 않을 때 Runtime GM이 다음 Situation을 생성한다.

### GM Turn

한 GM Turn은 내부적으로 resolution 및 output 검증을 거치지만 제품 관점의 확정 단위는 하나다. 정상적으로 player-visible 결과가 만들어지면 Game State와 Runtime-added Fact를 함께 commit한다. 안전한 narration을 만들지 못하면 commit하지 않는다.

## 8. Failures, Exceptions, and Boundary Conditions

- **Storybook 없음:** 컴파일 시작을 허용하지 않는다. Rulebook만으로 새 시나리오를 생성하지 않는다.
- **다중 Storybook인데 Primary 미지정:** 컴파일 전에 Primary 선택을 요구한다.
- **일반적인 source conflict:** Integration Prompt가 있으면 그 지시를 우선하고, 없으면 Primary 우선으로 최대한 자동 해결한다. 자동 해결된 conflict는 warning으로 남길 수 있다.
- **Compilation BLOCKED:** source fact 누락이나 conflict를 자동으로 해결할 수 없는 최후의 경우다. 정확한 문제 내용과 source 위치를 공개하고, 사용자가 바로 적용할 수 있는 수정안/Integration Prompt 예시를 제공한다.
- **Creativity 부족:** 현재 Creativity로 핵심 목표 진행·resolution에 필요한 누락 정보를 보완할 수 없으면 `BLOCKED`다. 자동 escalation하지 않는다.
- **Runtime source fact 없음 / retrieval 실패:** Turn을 중단하는 기본 사유가 아니다. 최소 fallback fact를 생성하고 Runtime-added Fact로 stage한다.
- **Fallback conflict:** 기존 Game State 또는 Runtime-added Fact를 우선하고 새 fallback을 재생성한다.
- **나중에 source conflict 발견:** 이미 commit된 Runtime-added Fact와 source가 충돌하면 해당 플레이스루에서는 Runtime-added Fact를 유지한다.
- **Hidden-information leak:** unsafe narration은 사용자에게 노출하지 않는다. 동일한 fixed Turn Resolution에서 narration만 다시 생성한다.
- **Repeated safe-output failure:** 해당 Turn의 상태/fact 변경을 commit하지 않고 generic retry 가능 결과를 보여준다.
- **Scenario Model에 없는 source detail:** locked Storybook RAG에서 실제 근거를 찾으면 정상적으로 사용할 수 있으며 Scenario Model을 수정하지 않는다.

## 9. Inputs and Outputs

### Preparation Inputs

| Input | Required | Notes |
|---|---:|---|
| Published Rulebook | Yes | 일반 게임 mechanics의 근거. 기존 catalog precondition을 따른다. |
| Storybook(s) | Yes | 최소 1개. Rulebook-Only 불가. |
| Primary Storybook | Conditional | 2개 이상일 때 필수. 1개면 자동 지정. |
| Integration Prompt | No | Storybook 간 충돌 해결용. 최초 컴파일 전부터 입력 가능하고 Adventure Start 전까지 수정·재컴파일 가능. |
| Compilation Creativity | Yes | 기본 `CONSERVATIVE`; `NONE / CONSERVATIVE / CREATIVE`. |

### Preparation Outputs

- hidden Scenario Model
- `READY` 또는 `BLOCKED` Compilation status
- conflict/warning diagnostics
- `BLOCKED`일 경우 spoiler를 포함할 수 있는 상세 문제 fact, source location, 실패 이유, 구체적 수정 제안

### Runtime Inputs

- Solo Player의 텍스트 행동 또는 확정된 맵 상호작용
- current Game State
- committed Runtime-added Facts
- relevant Scenario Model slice / compiled interpretation
- relevant locked Storybook RAG evidence
- relevant selected Rulebook RAG evidence
- 최근 플레이 context

### Runtime Outputs

- player-visible Scene / 행동 결과
- atomic Game State changes
- 필요 시 committed Runtime-added Facts
- 현재 Situation 유지 또는 새 Situation 파생
- Resolution Condition 충족 시 concluding Scene과 `COMPLETED` 상태

## 10. Scope and Non-goals

### In Scope

- 기존 Adventure Story Plan 기반 실행 모델 완전 대체
- Storybook-required Scenario Compilation
- Multi-Storybook Primary와 Integration Prompt 기반 충돌 통합
- 3단계 Compilation Creativity
- hidden Scenario Model과 Adventure Start Lock
- Scenario Model + RAG 기반 relevant-context Runtime
- Situation-driven, multi-turn GM 진행
- Runtime-added Fact fallback 및 continuity precedence
- Game State와 Runtime-added Fact의 구분
- hidden-information visibility policy와 narration regeneration
- atomic GM Turn commit
- resolution-based concluding Scene 및 Adventure completion

### Out of Scope / Non-goals

- Rulebook-Only 모험 생성
- `Adventure Story Plan`, Stage sequence, preplanned branch/ending execution을 새 flow에 유지하는 것
- Solo Player가 Scenario Model의 hidden contents를 열람·승인·직접 편집하는 기능
- Integration Prompt를 이용한 자유로운 전체 시나리오 재저작
- Compilation 시 optional world-building을 빈칸 채우기 목적으로 생성하는 것
- Runtime에서 Storybook 전체를 Scenario Model에 복제하거나 RAG로 발견한 source fact로 Scenario Model을 enrichment하는 것
- Player Notes 기능
- `COMPLETED` 이후 post-adventure free play
- framework, persistence, API/module structure 등 Architecture 단계의 구현 결정

## 11. Priorities and Trade-offs

충돌하는 요구가 있을 때 다음 우선순위를 따른다.

1. **Play continuity** — Runtime source 부족만으로 플레이를 끊지 않는다.
2. **Already-established play consistency** — commit된 Game State와 Runtime-added Fact를 retcon하지 않는다.
3. **Compiled interpretation** — Integration Prompt와 Scenario Compilation이 확정한 conflict decision을 Runtime retrieval이 뒤집지 않는다.
4. **Original source fidelity** — 위 원칙을 해치지 않는 범위에서 Storybook/Rulebook 원문을 최대한 따른다.
5. **Runtime improvisation** — source로 해결할 수 없을 때만 최소한으로 사용한다.

Preparation에서는 연속성보다 **startability와 source-grounded resolution**을 먼저 확보한다. 따라서 core objective/resolution 자체를 구성할 수 없고 현재 Compilation Creativity로 보완할 수 없는 경우에는 Adventure를 시작시키지 않고 `BLOCKED`한다. 반면 Adventure가 시작된 이후의 unforeseen source gap은 Runtime-added Fact fallback으로 처리한다.

## 12. Success Conditions and Acceptance Criteria

| ID | Acceptance Criterion |
|---|---|
| **AC-SMR-001** | Storybook 없이 새 Scenario Model adventure를 준비하려 하면 컴파일/시작이 차단되고 Rulebook-Only Adventure Story Plan 생성으로 우회하지 않는다. |
| **AC-SMR-002** | Storybook이 2개 이상이면 Primary 선택이 요구되고, Integration Prompt가 충돌 해석을 명시하면 해당 지시가 Primary 기본 우선순위보다 먼저 적용된다. |
| **AC-SMR-003** | Integration Prompt가 없는 직접 충돌에서는 Primary를 기본으로 통합하고 Supplement의 compatible/missing details를 유지한다. |
| **AC-SMR-004** | Creativity 기본값은 `CONSERVATIVE`이며 자동 escalation하지 않는다. `NONE`에서 core progression/resolution에 반드시 필요한 source-missing fact가 요구되면 Compilation은 `BLOCKED`한다. |
| **AC-SMR-005** | Compilation이 자동 보정으로 `READY`를 만들 수 있으면 사용자의 hidden story review 없이 시작 가능 상태가 된다. 자동 보정이 불가능하면 `BLOCKED`에서 실제 문제 fact, source location, 실패 이유와 구체적 해결 제안을 확인할 수 있다. |
| **AC-SMR-006** | `READY` Adventure를 시작하면 현재 Scenario Model/source set이 lock되고 첫 Situation은 그 시점의 Scenario Model + 초기 Game State + RAG로 생성된다. |
| **AC-SMR-007** | Runtime에서 Scenario Model에 없는 Storybook detail이 필요할 때 locked source RAG에서 근거를 찾으면 해당 fact를 사용할 수 있고 Scenario Model 자체는 변경되지 않는다. |
| **AC-SMR-008** | Runtime에서 필요한 scenario fact를 existing state/model/source에서 찾지 못하면 GM Turn은 기본적으로 중단되지 않고 fallback fact가 생성되어 Runtime-added Fact로 저장된다. Compilation Creativity 값이 이 fallback을 막지 않는다. |
| **AC-SMR-009** | commit된 Runtime-added Fact와 나중에 조회된 Storybook fact가 충돌하면 현재 플레이스루에서는 기존 Runtime-added Fact가 유지된다. |
| **AC-SMR-010** | 새 fallback fact가 기존 Game State 또는 Runtime-added Fact와 충돌하면 기존 플레이 기록을 바꾸지 않고 새 fallback을 다시 생성한다. |
| **AC-SMR-011** | 같은 Situation에서 여러 GM Turn이 진행될 수 있으며, Effective World State 변화로 현재 문제 상태가 바뀌었을 때만 새 Situation을 파생한다. |
| **AC-SMR-012** | GM의 hidden fact가 player-visible narration에 직접 노출되지 않고, 관찰·이전 공개·현재 행동에 의한 정당한 revelation만 출력된다. |
| **AC-SMR-013** | hidden-information leak가 검출되면 동일한 fixed Turn Resolution을 사용하여 narration만 다시 생성하며 dice, 판정 결과, Game State를 재계산하지 않는다. |
| **AC-SMR-014** | safe narration 생성이 반복 실패하면 unsafe output은 노출되지 않고 해당 Turn의 Game State/Runtime-added Fact 변경도 commit되지 않으며 사용자는 generic retry 결과를 받는다. |
| **AC-SMR-015** | 정상 GM Turn에서는 player-visible result, Game State 변경, 그 Turn의 새 Runtime-added Facts가 하나의 atomic 결과로 함께 commit된다. |
| **AC-SMR-016** | core Resolution Condition이 충족되면 동일 진행의 결과와 concluding Scene을 제공한 뒤 Adventure 상태가 `COMPLETED`가 되고 이후 자유 플레이로 자동 전환되지 않는다. |
| **AC-SMR-017** | Runtime GM은 전체 Storybook이나 전체 Scenario Model을 매 턴 전제로 하지 않고 현재 Situation에 필요한 structure/state/source evidence로 플레이를 지속할 수 있다. |
| **AC-SMR-018** | Scenario-specific encounter fact는 Storybook/Scenario Model 영역에서, 일반 게임 mechanics는 selected Rulebook 근거에서 사용되어 두 종류의 권위가 혼합되지 않는다. |

## Product Diagrams

- [UC-SMR-00 Use-case Diagram](diagrams/product/UC-SMR-00.usecase.svg)
- [UC-SMR-00 Activity Diagram](diagrams/product/UC-SMR-00.activity.svg)
- [Scenario Model Runtime Business-state Diagram](diagrams/product/scenario-model-runtime.business-state.svg)

Business-state diagram은 `READY/BLOCKED` 시작 gate, Adventure Start Lock, `RUNNING → COMPLETED` 전환이 독립적인 제품 검토 대상이므로 적용한다.

Class diagram은 Product 단계에서 생성하지 않는다.
