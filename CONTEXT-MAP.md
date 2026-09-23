# 컨텍스트 맵

## 1. 목적

이 문서는 저장소에서 세션을 넘어 유지되는 설계 경계만 정리한다.
세부 구현 절차나 실행 계획은 포함하지 않는다.

## 2. 경계

| Bounded Context | 책임 | 주 입력 | 주 출력 | 비고 |
|---|---|---|---|---|
| Document Knowledge | Knowledge Document 원본, Extraction Version, Source Span, Asset, 검색 인덱스 | 업로드 파일, 추출 요청, 검색 질의 | 불변 원문 추출본, STORYBOOK/RULEBOOK Evidence | `rule-knowledge-service` |
| Scenario Preparation | Scenario Source Bundle, Resolution Unit, Override, Scenario Package Version | 문서·추출 버전 참조, AI 추출 후보 | 검증된 Scenario Package | `adventure-service` 내부 경계 |
| Adventure Runtime | Runtime Binding, 프리플라이트, Active Source Context, Runtime Turn·Command 조정, CombatEncounter lifecycle·Initiative·Round·Combat Turn·Reaction 조정 | 플레이어 행동, Package Version, Evidence, 전투 행동 | 확정 세션 이벤트, 플레이어 응답, 전투 상태·Combat Log projection | `adventure-service` 내부 경계; Combat은 별도 Bounded Context/service가 아닌 내부 capability |
| AI Game Master | Resolution 후보, 시작 위치 후보, Runtime Plan, narration, 안전 검사 제안 | 제한된 근거와 버전된 스키마 | 저장 권한 없는 AI 후보·제안 | `ai-game-master-service` |
| Dice Roll | 주사위 실행과 결과 정본 | 멱등 Roll Command | 불변 Roll Result | `dice-roll-service` |
| Character Management | 캐릭터 HP, 인벤토리, 효과, 자원 | 버전 조건부 Character Command | 캐릭터 상태 | `character-management-service` |
| Combat Map | 지도, 토큰, 공간 요소, 칸별 이동 해결, 시야·탐험 상태 | 버전 조건부 Map Command | 전술 지도 상태와 공간적 사실 | `combat-map-service` |

## 3. 관계

- Scenario Preparation은 Document Knowledge의 Customer다. Document와 Extraction Version을 ID로 참조하며 원문을 복제하지 않는다.
- Scenario Preparation은 AI Game Master를 후보 생성 Provider로 사용한다. AI 결과는 반드시 Scenario Preparation에서 검증한 뒤 저장한다.
- Adventure Runtime은 Scenario Preparation의 게시된 Package Version만 사용한다.
- Adventure Runtime과 Scenario Preparation은 Document Knowledge의 통합 근거 후보 검색 계약을 사용한다. 각 작업의 상황별 근거 충분성 정책이 STORYBOOK, RULEBOOK 또는 두 유형을 검색 대상으로 선택하며, Document Knowledge는 선택된 범위 안에서 Dense·BM25 후보를 RRF 방식으로 통합한다.
- Adventure Runtime과 Scenario Preparation은 AI Game Master를 관련도 재정렬과 근거 충분성 판단의 제안 Provider로 사용한다. AI 결과의 후보 식별자, 범위, 형식은 Adventure 서비스가 검증하고, 상황별 최종 결과도 Adventure 서비스가 확정한다.
- Adventure Runtime은 AI Game Master의 제안을 검증되지 않은 상태 변경으로 취급한다.
- Adventure Runtime은 모험별 대화 원문, 확정 턴에서 유래한 대화 요약, 현재 상황에 관련된 확정 사실별 장기 기록을 소유한다. AI Game Master는 압축된 대화와 장기 기록의 변경 후보만 생성하며, Adventure Runtime이 확정 근거·공개 여부·버전을 검증한 뒤 저장한다. 캐릭터 시트와 Current Situation의 정본은 기존 소유자가 유지한다.
- Adventure Runtime은 Dice Roll, Character Management, Combat Map의 상태를 복제하지 않고 Runtime Command Saga로 조정한다.
- Adventure Runtime의 `CombatEncounter` Aggregate는 전투 lifecycle과 순서를 소유한다. Dice Roll은 굴림 결과, Character Management는 캐릭터 상태, Combat Map은 지도·위치 상태의 정본을 계속 소유한다.
- AI Game Master는 전투 시작·종료, Free-form Action, AI Turn과 AI Reaction에 대해 구조화된 제안만 제공한다. Adventure Runtime의 Game Engine과 `CombatEncounter`가 제안을 검증하고 상태 변경을 확정한다.
- AI Game Master는 자연어 이동과 공간 요소 위치를 구조화된 후보로만 제안한다. Combat Map은 확인된 이동과 검증된 배치만 지도 상태에 반영한다.
- Combat Map은 확인된 이동을 칸 순서대로 해결하고 공간 요소 반응과 적의 인지 같은 공간적 사실을 Adventure Runtime에 전달한다. Adventure Runtime은 룰북 판정, 행동 비용, 전투·경고·대화·추격 같은 진행 결과를 확정한다.
- Adventure Runtime은 Combat Map의 이동 예약을 조정하고, 요청된 지각·해제·내성·피해 판정을 해결한 결과를 돌려준다. Combat Map은 최종 반영 전 실패에서 예약을 취소하고, 최종 반영 뒤의 캐릭터 HP·상태·자원 변경은 Adventure Runtime이 기존 Runtime Command Saga로 완료한다.
- Combat Map은 함정의 위치·공개 상태·발동 상태와 이동 중단을 소유한다. Dice Roll은 주사위 결과를, Character Management는 HP·상태·자원 변경을 계속 소유한다.
- Adventure Runtime은 Combat snapshot과 Event를 적 비공개 정보가 제거된 player projection으로 변환해 REST와 SSE로 제공한다.
