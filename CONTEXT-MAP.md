# 컨텍스트 맵

## 1. 목적

이 문서는 저장소에서 세션을 넘어 유지되는 설계 경계만 정리한다.
세부 구현 절차나 실행 계획은 포함하지 않는다.

## 2. 경계

| Bounded Context | 책임 | 주 입력 | 주 출력 | 비고 |
|---|---|---|---|---|
| Document Knowledge | Knowledge Document 원본, Extraction Version, Source Span, Asset, 검색 인덱스 | 업로드 파일, 추출 요청, 검색 질의 | 불변 원문 추출본, STORYBOOK/RULEBOOK Evidence | `rule-knowledge-service` |
| Scenario Preparation | Scenario Source Bundle, Resolution Unit, Override, Scenario Package Version, 모험별 초기 필수 상황과 중간 상황 후보 준비 | 문서·추출 버전 참조, 모험 식별자, AI 추출 후보 | 검증된 Scenario Package, 다른 모험과 공유하지 않는 초기 상황 세트 | `adventure-service` 내부 경계 |
| Adventure Runtime | Runtime Binding, 프리플라이트, Active Source Context, Runtime Turn·Command 조정, 모험별 출처 범위의 적 캐릭터 시트 준비·재사용, CombatEncounter lifecycle·Initiative·Round·Combat Turn·Reaction 조정, 행동/이동 예산, 적 HP·효과·집중 상태, 승인된 모험 준비 정의와 실행 명령 처리 기록 | 플레이어 행동, Package Version, Evidence, 전투 행동 | 확정 세션 이벤트, 플레이어 응답, 검증된 적 캐릭터 시트·전투 상태·Combat Log projection 및 적 상태 변경 결과 | `adventure-service` 내부 경계; Combat과 적 시트는 별도 Bounded Context/service가 아닌 내부 capability |
| AI Game Master | Resolution 후보, 시작 위치 후보, Runtime Plan, narration, 안전 검사 제안 | 제한된 근거와 버전된 스키마 | 저장 권한 없는 AI 후보·제안 | `ai-game-master-service` |
| Dice Roll | 주사위 실행과 결과 정본 | 멱등 Roll Command | 불변 Roll Result | `dice-roll-service` |
| Character Management | 플레이어·동료 HP/효과/집중 상태, 인벤토리·권위 수량·아이템 식별자, 주문 사용 권한·슬롯, 공격/내성/시전 능력치 | 버전 조건부 Character Command | 플레이어·동료 캐릭터 상태 | `character-management-service` |
| Combat Map | 지도, 토큰 위치·고도·공개 상태, 공간 요소, 칸별 이동 해결, 시야·탐험 상태 및 기하/차단 | 버전 조건부 Map Command | 전술 지도 상태, 공간적 사실, 실제 도달 경로와 이동 비용 | `combat-map-service` |

## 3. 관계

- Scenario Preparation은 Document Knowledge의 Customer다. Document와 Extraction Version을 ID로 참조하며 원문을 복제하지 않는다.
- Scenario Preparation은 AI Game Master를 후보 생성 Provider로 사용한다. AI 결과는 반드시 Scenario Preparation에서 검증한 뒤 저장한다.
- Adventure Runtime은 Scenario Preparation의 게시된 Package Version만 사용한다.
- 새로 준비하는 모험의 필수 상황과 중간 상황 후보는 Scenario Preparation이 모험별로 독립 생성·저장한다. Adventure Runtime은 해당 모험의 후보 선택·사용 상태와 플레이 중 새로 만든 상황을 소유하고 기존 턴의 현재 상황 변경과 함께 검증·확정한다. 다른 모험의 상황 자료를 재사용하거나 변경하지 않는다.
- Adventure Runtime과 Scenario Preparation은 Document Knowledge의 통합 근거 후보 검색 계약을 사용한다. 각 작업의 상황별 근거 충분성 정책이 STORYBOOK, RULEBOOK 또는 두 유형을 검색 대상으로 선택하며, Document Knowledge는 선택된 범위 안에서 Dense·BM25 후보를 RRF 방식으로 통합한다.
- Adventure Runtime과 Scenario Preparation은 AI Game Master를 관련도 재정렬과 근거 충분성 판단의 제안 Provider로 사용한다. AI 결과의 후보 식별자, 범위, 형식은 Adventure 서비스가 검증하고, 상황별 최종 결과도 Adventure 서비스가 확정한다.
- Adventure Runtime은 적 캐릭터 시트 후보의 출처와 완전성을 검증해 모험별 고정 지식 자료 범위 안에서 저장·재사용한다. AI Game Master는 적 전투 수치, 행동, 규칙 판정 근거를 포함한 후보를 만들고 Document Knowledge는 선택된 자료에서 출처 증거를 제공한다.
- Adventure Runtime은 AI Game Master의 제안을 검증되지 않은 상태 변경으로 취급한다.
- Adventure Runtime은 모험별 대화 원문, 확정 턴에서 유래한 대화 요약, 현재 상황에 관련된 확정 사실별 장기 기록을 소유한다. AI Game Master는 압축된 대화와 장기 기록의 변경 후보만 생성하며, Adventure Runtime이 확정 근거·공개 여부·버전을 검증한 뒤 저장한다. 캐릭터 시트와 Current Situation의 정본은 기존 소유자가 유지한다.
- Adventure Runtime은 Dice Roll, Character Management, Combat Map의 상태를 복제하지 않고 기존 전투 명령 처리 기록으로 조정한다. 각 소유자는 자기 데이터 변경과 명령 ID별 저장 처리 결과/지문을 같은 로컬 트랜잭션으로 확정한다. Character Management는 기존 `CharacterSheetApplicationService.applyRuntimeMutation` 및 `character_sheet_command_history` 재생을 사용하고, 슬롯과 소모 아이템은 한 캐릭터 변경으로 함께 확정한다.
- Adventure Runtime의 `CombatEncounter` Aggregate는 전투 lifecycle·턴·행동 및 이동 예산, 적 HP·효과·집중 상태와 순서를 소유한다. Character Management는 플레이어·동료 HP·효과·집중·아이템 수량·주문 권한/슬롯·전투 능력치를, Dice Roll은 불변 굴림 결과를, Combat Map은 위치·고도·공개 상태와 기하·차단 및 실행된 실제 이동 결과를 계속 소유한다. AI와 클라이언트는 제안과 선택만 제공한다.
- AI Game Master는 전투 시작·종료, Free-form Action, AI Turn과 AI Reaction에 대해 구조화된 제안만 제공한다. Adventure Runtime의 Game Engine과 `CombatEncounter`가 제안을 검증하고 상태 변경을 확정한다.
- AI Game Master는 자연어 이동과 공간 요소 위치를 구조화된 후보로만 제안한다. Combat Map은 확인된 이동과 검증된 배치만 지도 상태에 반영한다.
- Combat Map은 확인된 이동을 칸 순서대로 해결하고 공간 요소 반응과 적의 인지 같은 공간적 사실을 Adventure Runtime에 전달한다. Adventure Runtime은 룰북 판정, 행동 비용, 전투·경고·대화·추격 같은 진행 결과를 확정한다.
- Adventure Runtime은 동일 전투 명령 처리 기록 안에서 이동 예산을 예약한다. Combat Map은 실행 시 기대 지도 버전으로 기하/차단을 권위 검증하고 실제 도달 경로와 비용을 확정한다. Adventure Runtime은 실제 이동 비용과 encounter 중복 방지 기록을 함께 확정한다. 기존 독립 지도 재생 처리는 전투 예산을 다시 차감하거나 우회할 수 없다.
- 플레이어·동료의 주문 슬롯과 소모 아이템 변경은 Character Management의 기존 명령 기록/재생 계약으로 처리하며, 적 상태와 전투 턴 자원은 Adventure Runtime이 확정한다. 소유자 처리 결과가 불명확하면 같은 명령 ID로 조회/재시도하고 예약과 대기 행동을 유지한다. 상태/자원 변경 전 명확한 거부는 예약을 해제하며, 일부 변경 후에는 저장된 나머지 단계만 재개한다. encounter와 operation 단계 기록은 원자적으로 함께 갱신되어 중복 적용을 막는다.
- Combat Map은 함정의 위치·공개 상태·발동 상태와 이동 중단을 소유한다. Dice Roll은 주사위 결과를, Character Management는 HP·상태·자원 변경을 계속 소유한다.
- Adventure Runtime은 Combat snapshot과 Event를 적 비공개 정보가 제거된 player projection으로 변환해 REST와 SSE로 제공한다.
