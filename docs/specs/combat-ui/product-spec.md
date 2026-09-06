# Product Spec — 전용 Combat UI

## 1. Problem and Context

현재 세션 플레이는 일반 세션 UI를 중심으로 진행되지만, 전투에 진입하면 Round, Turn, Initiative, 이동, Action, Bonus Action, Reaction, 상태 효과 등 일반 탐험과 다른 정보와 조작이 집중적으로 필요하다.

전투 중에도 일반 세션 UI만 사용할 경우 현재 턴, 남은 행동 자원, 행동 가능 여부, 대상, 전투 진행 상황을 명확하게 파악하기 어렵다.

따라서 전투가 시작되면 플레이어가 전투에 필요한 정보와 행동에 집중할 수 있는 별도의 Combat UI로 자동 전환한다.

---

## 2. Goals and Desired Outcomes

- 전투 시작과 동시에 전투 전용 화면으로 자연스럽게 전환한다.
- Round와 Turn의 진행 상황을 명확하게 보여준다.
- D&D 전투의 정형 행동을 빠르게 실행할 수 있게 한다.
- 정형화되지 않은 TRPG 특유의 자유로운 행동 선언을 제한하지 않는다.
- AI 파티원과 AI GM이 담당하는 캐릭터의 턴은 자동 진행한다.
- 규칙적으로 불가능한 행동은 실행 전에 명확한 이유와 함께 차단한다.
- 전투 종료 후 일반 세션 플레이로 자연스럽게 복귀한다.
- 맵의 존재 여부와 관계없이 전투를 진행할 수 있어야 한다.

---

## 3. Users and Actors

### Human Player

- 인간 사용자는 1명이다.
- 자신의 플레이어 캐릭터 1명을 직접 조작한다.

### AI Party Members

- 인간 플레이어 외의 파티 캐릭터.
- 자신의 Turn이 되면 AI가 자동으로 행동한다.
- 플레이어가 사전에 내린 간단한 전술 지시를 참고할 수 있다.
- 매 행동마다 플레이어 승인을 요구하지 않는다.

### AI GM

- 적과 NPC의 행동을 결정한다.
- 자유 행동 선언을 해석한다.
- 필요한 판정과 행동 비용을 결정한다.
- 전투 시작·종료 조건을 판단한다.
- 플레이어에게 공개하면 안 되는 적 정보를 관리한다.

### Game Engine

- 주사위 판정
- 규칙 검증
- 자원 소비
- HP 및 상태 변화
- 이동 가능 여부
- 전투 상태 변경

등 결정론적으로 처리 가능한 결과를 검증하고 상태에 반영한다.

---

## 4. Ubiquitous Language and Terminology

### Combat Mode

전투 중 활성화되는 플레이어 전용 UI 상태.

### Combat Preparation

전투 참가자를 확정하고 Initiative를 결정하여 Round 1을 시작하기 전까지의 준비 단계.

### Round

모든 전투 참가자가 Initiative 순서에 따라 Turn을 수행하는 전투 진행 단위.

### Turn

특정 전투 참가자가 자신의 행동을 수행하는 단위.

### Movement

Turn 동안 사용할 수 있는 이동량.

### Action

공격, Dodge, Dash 등 일반적으로 Turn에 사용할 수 있는 주 행동 자원.

### Bonus Action

능력·주문 등이 허용하는 경우 사용할 수 있는 보너스 행동 자원.

### Reaction

특정 Trigger가 발생했을 때 자신의 Turn 외에도 사용할 수 있는 반응 행동.

### Free-form Action Declaration

정형 행동 목록에 존재하지 않는 플레이어의 자유로운 행동 선언.

별도의 무제한 행동 자원을 의미하지 않는다. AI GM이 선언을 해석하여 Action, Bonus Action, 기타 상호작용 등의 비용과 판정을 결정한다.

### Combat Log

현재 전투에서 발생한 행동과 규칙 처리 결과를 시간순으로 보여주는 기록.

---

## 5. Core Use Cases

### UC-001 전투 진입

1. 일반 세션을 진행한다.
2. AI GM이 전투 발생을 확정한다.
3. Combat Preparation 상태로 전환한다.
4. 참가자를 확정한다.
5. Initiative를 자동 판정한다.
6. Initiative 순서를 표시한다.
7. Round 1을 시작한다.
8. Initiative 1위 참가자의 Turn을 시작한다.
9. Combat UI를 표시한다.

---

### UC-002 플레이어 Turn 진행

1. 현재 Round와 자신의 Turn을 확인한다.
2. 현재 Movement / Action / Bonus Action / Reaction 상태를 확인한다.
3. 원하는 순서대로 이동과 행동을 수행한다.
4. Action과 Bonus Action 사이에 이동을 나누어 사용할 수 있다.
5. 필요한 경우 자유 행동을 선언한다.
6. 플레이어가 `턴 종료`를 누른다.
7. 다음 Initiative 참가자의 Turn으로 진행한다.

플레이어의 Turn은 시스템이 자동 종료하지 않는다.

---

### UC-003 정형 행동 실행

1. 플레이어가 공격, 주문, 아이템 등의 행동을 선택한다.
2. 필요한 대상을 지정한다.
3. 시스템이 사거리 및 행동 조건을 검증한다.
4. 적용될 주요 판정 정보를 표시한다.
5. 플레이어가 실행한다.
6. 필요한 주사위를 자동 판정한다.
7. 결과를 상태에 반영한다.
8. 전투 로그에 기록한다.

---

### UC-004 이동

맵이 존재하는 경우:

1. `이동`을 선택한다.
2. 전투 맵에서 목적지를 선택한다.
3. 시스템이 이동 거리와 이동 가능 여부를 계산한다.
4. 결과를 플레이어에게 표시한다.
5. 플레이어가 이동을 확정한다.
6. 남은 이동량을 갱신한다.

맵이 없는 경우 AI GM이 서술된 공간 관계를 바탕으로 이동 가능 여부를 판단한다.

---

### UC-005 자유 행동 선언

1. 플레이어가 정형 행동 목록에 없는 행동을 입력한다.
2. AI GM이 행동의 의도를 해석한다.
3. 필요한 행동 자원과 판정을 결정한다.
4. 필요한 경우 게임 엔진에서 판정한다.
5. 결과를 전투 상태에 반영한다.

예:

- 소지품의 햄을 고블린에게 던진다.
- 샹들리에의 줄을 자른다.
- 적에게 항복하라고 외친다.
- 주변 물체를 임시 엄폐물로 사용한다.

자유 행동 입력 자체는 정형 규칙에 매핑되지 않는다는 이유로 차단하지 않는다.

---

### UC-006 Reaction 처리

1. 현재 참가자가 Turn을 진행한다.
2. Reaction Trigger가 발생한다.
3. 현재 진행을 일시 정지한다.
4. 해당 플레이어에게 Reaction 선택 UI를 표시한다.
5. 플레이어가 Reaction을 사용하거나 넘긴다.
6. 결과를 처리한다.
7. 중단됐던 Turn을 계속한다.

---

### UC-007 AI 파티원 Turn

1. AI 파티원의 Turn이 시작된다.
2. 현재 전투 상태와 자신의 캐릭터 상태를 확인한다.
3. 플레이어가 준 전술 지시가 있다면 참고한다.
4. 행동을 자동 결정한다.
5. 규칙 검증과 판정을 수행한다.
6. 결과를 표시하고 전투 로그에 기록한다.
7. Turn을 종료한다.

플레이어 승인을 매 행동마다 요구하지 않는다.

---

### UC-008 적/NPC Turn

1. AI GM이 적 또는 NPC의 행동을 결정한다.
2. 이동·공격·주문 등의 과정을 순서대로 처리한다.
3. 결과를 전투 화면 및 로그에 표시한다.
4. 플레이어 Reaction이 발생하면 진행을 중단한다.
5. Reaction 해결 후 Turn을 계속한다.
6. Turn을 종료한다.

---

### UC-009 전투 종료

AI GM은 다음을 포함한 상황을 통해 전투 종료를 판정할 수 있다.

- 적 전멸
- 적의 항복
- 적의 도주
- 플레이어의 성공적인 도주
- 협상을 통한 적대 상태 해소
- 시나리오상의 전투 목표 달성
- 기타 상황상 더 이상 Combat Mode가 필요하지 않은 경우

종료되면:

1. 최종 전투 결과를 반영한다.
2. 종료 결과를 현재 전투 로그에 표시한다.
3. Combat Mode를 종료한다.
4. 일반 세션 UI로 자동 복귀한다.

---

### UC-010 전투 재개

1. 전투 중 사용자가 세션을 이탈한다.
2. 진행 중 전투 상태를 보존한다.
3. 사용자가 동일 세션에 재진입한다.
4. 진행 중인 전투가 존재하면 Combat UI로 복귀한다.
5. 저장된 Round, Turn 및 해당 Turn의 남은 자원을 복원한다.

---

## 6. Business Rules and Invariants

### BR-001 전투 진입

Combat Mode는 AI GM이 전투 발생을 확정했을 때만 시작된다.

### BR-002 Round 진행

Round가 시작되면 Initiative 순서에 따라 참가자의 Turn을 진행한다.

마지막 참가자의 Turn이 끝나면 다음 Round가 시작된다.

### BR-003 Initiative

Initiative는 Combat Preparation에서 자동으로 판정한다.

결과와 순서는 플레이어에게 표시한다.

### BR-004 플레이어 Turn 종료

플레이어 캐릭터의 Turn은 반드시 플레이어가 `턴 종료`를 명시적으로 실행해야 종료된다.

### BR-005 행동 자원

Combat UI는 최소한 다음 자원의 현재 상태를 구분하여 표시한다.

- Movement
- Action
- Bonus Action
- Reaction

### BR-006 자유 행동

Free-form Action Declaration은 독립된 추가 행동 자원이 아니다.

AI GM이 선언 내용을 해석하여 실제 비용과 판정을 결정한다.

### BR-007 정형 행동 검증

정형 행동은 실행 전에 규칙적으로 가능한지 검증한다.

실행 불가능하면:

- 행동을 실행하지 않는다.
- Action 등의 자원을 소비하지 않는다.
- 실패 이유를 플레이어에게 표시한다.

### BR-008 자유 행동 검증

자유 행동은 정형 규칙에 즉시 매핑되지 않는다는 이유만으로 거부하지 않는다.

AI GM이 먼저 해석한다.

### BR-009 주사위

전투에서 필요한 주사위 판정은 기본적으로 자동 수행한다.

플레이어는 굴림 값, 보정치, 성공/실패 및 최종 결과를 확인할 수 있다.

### BR-010 적 정보 비공개

플레이어에게 다음 정보를 기본적으로 직접 노출하지 않는다.

- 정확한 적 HP
- 정확한 AC
- 비공개 능력치
- 아직 알려지지 않은 능력 및 정보

대신 관찰 가능한 부상 상태, 상태 이상, 플레이 중 공개된 정보는 표시할 수 있다.

### BR-011 AI Turn

AI 파티원과 적/NPC의 Turn은 플레이어 승인 없이 자동 진행한다.

단, 플레이어 선택이 필요한 Reaction 발생 시 진행을 중지한다.

### BR-012 맵 독립성

맵이 없어도 전투의 모든 핵심 기능을 사용할 수 있어야 한다.

---

## 7. States and State Transitions

전투의 사용자 관점 상태는 다음과 같다.

`일반 세션`

→ 전투 확정

`Combat Preparation`

→ 참가자 및 Initiative 확정

`Round 진행`

→ 현재 참가자의 Turn

`Player Turn | AI Party Turn | Enemy/NPC Turn`

→ 모든 참가자의 Turn 완료

`다음 Round`

또는

→ 전투 종료 조건 충족

`Combat End`

→ 일반 세션 복귀

Reaction은 독립적인 장기 상태가 아니라 진행 중 Turn을 일시 중단하는 Interrupt 상태로 취급한다.

---

## 8. Failures, Exceptions, and Boundary Conditions

### 실행 불가능한 정형 행동

실행을 차단하고 이유를 표시한다.

행동 자원은 소비하지 않는다.

### 사거리 밖 대상

대상 선택 또는 행동 실행 단계에서 차단하고 이동 또는 다른 대상 선택을 유도한다.

### 주문 슬롯 부족

실행하지 않고 부족한 자원을 표시한다.

### Reaction Trigger

현재 Turn 진행을 중단하되 기존 Turn 상태를 유지한다.

Reaction 처리 후 같은 지점에서 계속한다.

### 전투 중 세션 이탈

현재 전투 상태를 잃지 않는다.

재진입 시 그대로 복원한다.

### 맵 부재

전투 시작을 차단하지 않는다.

AI GM의 공간 서술 및 판정을 이용한다.

---

## 9. Inputs and Outputs

### Combat UI 필수 영역

#### 1. Combat Header

- 현재 Round
- 현재 Turn 주체
- 현재 전투 상태

#### 2. Initiative Tracker

- 참가자 순서
- 현재 Turn 강조
- 아군/적/NPC 구분
- 전투 이탈 또는 행동 불능 상태 표시

#### 3. Battlefield

맵이 있는 경우:

- 전투 맵
- 참가자 위치
- 목적지 선택
- 대상 선택
- 이동 거리/가능 여부

맵이 없는 경우:

- 공간 관계에 대한 AI GM 서술

#### 4. Player Character Status

상시 표시:

- 현재 HP / 최대 HP / 임시 HP
- AC
- 이동력 / 남은 이동량
- Action 상태
- Bonus Action 상태
- Reaction 상태
- 상태 이상
- Concentration

확장 영역:

- 주문 슬롯
- 클래스 자원
- 장비 상세
- 주요 공격 수단

#### 5. Action Panel

- Movement
- Action
- Bonus Action
- Reaction 상태
- 공격
- 주문
- 아이템
- 일반 전투 행동
- 자유 행동 선언
- 턴 종료

#### 6. Target / Participant Information

- 선택한 대상
- 공개 가능한 상태
- 알려진 정보
- 상태 효과

#### 7. Combat Log

현재 전투 동안 최소한 다음을 표시한다.

- Round 변경
- Turn 변경
- 행동 선언
- AI GM 판정
- 주사위 결과와 보정치
- 피해
- 회복
- 상태 변화
- Reaction
- 전투 종료 결과

전투 종료 후 상세 Combat Log를 별도 기록으로 제공할 필요는 없다.

#### 8. Reaction Interrupt UI

Reaction Trigger 발생 시 현재 전투 UI 위에 선택 인터럽트를 표시한다.

#### 9. GM Narration / Free Input

Combat Log와 별도로 다음을 제공한다.

- AI GM 상황 묘사
- NPC/AI 파티원 대사
- 플레이어 자유 발화
- Free-form Action Declaration

---

## 10. Scope and Non-goals

### In Scope

- 싱글 플레이어 전투
- 인간 플레이어 캐릭터 1명 직접 조작
- AI 파티원 자동 행동
- AI GM
- Combat Mode 자동 전환
- Combat Preparation
- Initiative
- Round / Turn
- Movement / Action / Bonus Action / Reaction
- 정형 행동
- Free-form Action Declaration
- Reaction Interrupt
- 자동 주사위 판정
- Combat Log
- GM Narration
- 맵 기반 전투
- 맵 없는 전투
- 전투 상태 저장 및 재개
- AI GM 기반 전투 종료

### Out of Scope

- 여러 인간 플레이어가 참가하는 멀티플레이
- 실시간 다중 사용자 Turn 동기화
- GM 전용 UI
- 전투 리플레이
- 전투 종료 후 상세 Combat Log 조회
- 화려한 전투 애니메이션 및 시각 효과
- AI 파티원의 전투 전략 품질 고도화

---

## 11. Priorities and Trade-offs

### P0 — 핵심 전투 루프

- Combat Mode
- Preparation / Initiative
- Round / Turn
- Player Action
- AI Turn
- Combat End

### P0 — 전투 행동

- Movement
- Action
- Bonus Action
- Reaction
- 공격 / 주문 / 아이템
- 자유 행동 선언

### P0 — 상태 및 정보

- Character Status
- Initiative Tracker
- Combat Log
- 자동 주사위
- 규칙 검증

### P0 — 전투 방식

- 맵 기반 전투
- 맵 없는 전투

### P1

- AI 파티원에 대한 간단한 전술 지시

### Deferred

- AI 전술 품질 고도화
- 애니메이션
- 리플레이
- 멀티플레이

자유로운 TRPG 플레이와 규칙 자동화가 충돌하면 **플레이어의 행동 선언 자체는 최대한 허용하고 AI GM + 게임 엔진을 통해 판정하는 것**을 우선한다.

---

## 12. Success Conditions and Acceptance Criteria

### AC-001

AI GM이 전투를 시작하면 일반 세션 UI에서 Combat UI로 자동 전환된다.

### AC-002

Combat Preparation에서 Initiative가 자동 판정되고 Initiative Tracker에 표시된다.

### AC-003

Round 번호와 현재 Turn 참가자를 항상 식별할 수 있다.

### AC-004

플레이어는 자신의 Turn에서 Movement / Action / Bonus Action / Reaction 상태를 확인할 수 있다.

### AC-005

플레이어는 이동을 행동 전후로 나누어 사용할 수 있다.

### AC-006

맵이 있는 경우 `이동 선택 → 목적지 선택 → 거리/가능 여부 확인 → 확정` 흐름으로 이동할 수 있다.

### AC-007

공격 및 주문 등 정형 행동은 `선택 → 대상 지정 → 검증 → 실행 → 판정 → 결과 반영` 흐름을 따른다.

### AC-008

불가능한 정형 행동은 자원을 소비하지 않고 차단되며 이유가 표시된다.

### AC-009

플레이어는 정형 목록에 없는 자유 행동을 언제든 선언할 수 있다.

### AC-010

AI GM은 자유 행동을 해석하여 필요한 판정과 행동 비용을 결정할 수 있다.

### AC-011

Reaction Trigger 발생 시 현재 Turn이 일시 중단되고 플레이어에게 선택권이 제공된다.

### AC-012

AI 파티원 및 적/NPC의 Turn은 자동으로 진행된다.

### AC-013

AI Turn 도중 플레이어 Reaction이 발생하면 자동 진행이 중단된다.

### AC-014

적의 비공개 HP·AC·능력치는 플레이어에게 노출되지 않는다.

### AC-015

전투 중 발생한 행동과 판정 결과를 Combat Log에서 확인할 수 있다.

### AC-016

Combat Log와 AI GM의 서술/자유 입력 영역은 분리되어 있다.

### AC-017

맵이 없어도 전투를 끝까지 진행할 수 있다.

### AC-018

플레이어 Turn은 `턴 종료` 입력 전까지 자동 종료되지 않는다.

### AC-019

전투 도중 세션에서 이탈했다가 돌아와도 Round, Turn 및 해당 Turn의 남은 자원이 복구된다.

### AC-020

전투 종료 조건이 충족되면 AI GM이 Combat Mode를 종료하고 일반 세션 UI로 자동 복귀시킨다.

### AC-021

전투 종료 후 HP, 자원, 상태, 인벤토리 등 실제 게임 상태 변경은 일반 세션에 유지된다.

### AC-022

상세 Combat Log는 전투 종료 후 별도 리플레이 기록으로 유지할 필요가 없다.

---

## Product Diagram Contract

이번 변경은 새로운 사용자 흐름을 추가하므로 Product 단계에서 다음 다이어그램이 필요하다.

- Combat UI Use Case Diagram: [UC-CUI.usecase.svg](diagrams/product/UC-CUI.usecase.svg)
- Combat Lifecycle Activity Diagram: [UC-CUI.activity.svg](diagrams/product/UC-CUI.activity.svg)
- Editable originals:
  - `diagrams/product/UC-CUI.usecase.puml`
  - `diagrams/product/UC-CUI.activity.puml`

Reaction 흐름은 Combat Lifecycle 또는 관련 Use Case의 Activity Diagram 안에서 표현한다.

별도의 Product Class Diagram은 작성하지 않는다.
