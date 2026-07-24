# 컨텍스트 맵

## 1. 목적

이 문서는 저장소에서 세션을 넘어 유지되는 설계 경계만 정리한다.
세부 구현 절차나 실행 계획은 포함하지 않는다.

## 2. 경계

| Bounded Context | 책임 | 주 입력 | 주 출력 | 비고 |
|---|---|---|---|---|
| Document Knowledge | Knowledge Document 원본, Extraction Version, Source Span, Asset, 검색 인덱스 | 업로드 파일, 추출 요청, 검색 질의 | 불변 원문 추출본, STORYBOOK/RULEBOOK Evidence | `rule-knowledge-service` |
| Scenario Preparation | Scenario Source Bundle, Resolution Unit, Override, Scenario Package Version | 문서·추출 버전 참조, AI 추출 후보 | 검증된 Scenario Package | `adventure-service` 내부 경계 |
| Adventure Runtime | Runtime Binding, 프리플라이트, Active Source Context, Runtime Turn·Command 조정 | 플레이어 행동, Package Version, Evidence | 확정 세션 이벤트, 플레이어 응답 | `adventure-service` 내부 경계 |
| AI Game Master | Resolution 후보, 시작 위치 후보, Runtime Plan, narration, 안전 검사 제안 | 제한된 근거와 버전된 스키마 | 저장 권한 없는 AI 후보·제안 | `ai-game-master-service` |
| Dice Roll | 주사위 실행과 결과 정본 | 멱등 Roll Command | 불변 Roll Result | `dice-roll-service` |
| Character Management | 캐릭터 HP, 인벤토리, 효과, 자원 | 버전 조건부 Character Command | 캐릭터 상태 | `character-management-service` |
| Combat Map | 지도, 토큰, 이동 상태 | 버전 조건부 Map Command | 전투 지도 상태 | `combat-map-service` |

## 3. 관계

- Scenario Preparation은 Document Knowledge의 Customer다. Document와 Extraction Version을 ID로 참조하며 원문을 복제하지 않는다.
- Scenario Preparation은 AI Game Master를 후보 생성 Provider로 사용한다. AI 결과는 반드시 Scenario Preparation에서 검증한 뒤 저장한다.
- Adventure Runtime은 Scenario Preparation의 게시된 Package Version만 사용한다.
- Adventure Runtime은 Document Knowledge의 STORYBOOK Evidence와 Session Knowledge Set의 RULEBOOK Evidence를 분리 조회한다.
- Adventure Runtime은 AI Game Master의 제안을 검증되지 않은 상태 변경으로 취급한다.
- Adventure Runtime은 Dice Roll, Character Management, Combat Map의 상태를 복제하지 않고 Runtime Command Saga로 조정한다.
