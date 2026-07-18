# 도메인 아키텍처

이 시스템은 7개 bounded context로 나뉘며, 대부분의 도메인 경계는 `internal_http`와 정적 계약으로 연결된다. AI Game Master BC는 별도 애그리게이트를 두지 않고, Adventure와 Dice Roll, Combat Map의 흐름을 조율한다.

## Bounded Context

| BC | 책임 | 주 연결 |
| --- | --- | --- |
| Identity & Access | 플레이어 인증과 소유자 식별 | Adventure, Rule Knowledge |
| Adventure | 모험 상태, 시나리오, 적용 룰셋, 질의 흐름 | Identity & Access, Rule Knowledge, Character Management, Dice Roll, Combat Map, AI Game Master |
| Rule Knowledge | 룰북 업로드, 추출, 인덱싱, 근거 검색 | Identity & Access, Adventure |
| Character Management | 캐릭터 시트 조회와 갱신 | Adventure |
| Dice Roll | 주사위 실행과 판정 결과 저장 | Adventure, AI Game Master |
| Combat Map | 전투 맵, 토큰, 이동, 가시성 | Adventure, AI Game Master |
| AI Game Master | 장면 진행, NPC/서술/판정 오케스트레이션 | Adventure, Dice Roll, Combat Map |

## Aggregate

| Aggregate | 핵심 상태 |
| --- | --- |
| Player | 플레이어 식별자와 소유 경계 |
| Adventure | 세션, 소유자, 시나리오, 룰셋, 캐릭터 시트, 대화 상태, 현재 진행 맥락, 저장 상태 |
| AdventureScenario | 업로드 소스와 준비 상태 |
| AppliedRuleSet | 선택 에디션과 룰북 조합 |
| RuleInquiry | 질의 상태, 근거 상태, 답변, 후보 규칙, 근거 위치 |
| Rulebook | 파일 형식, 크기, 처리 상태, 추출 결과, 분할 결과 |
| RulebookIndex | 룰북 검색 인덱스와 근거 데이터 |
| CharacterSheet | 시트 에디션과 캐릭터 데이터 |
| DiceRoll | 롤 범위, 표현식, 결과 |
| CombatMap | 그리드, 토큰, 이동 경로, 레이어, 가시성 |

## 핵심 규칙

- 플레이어 소유권은 `PlayerId`와 `OwnerPlayerId`를 기준으로 검증한다.
- 룰북은 업로드 후 바로 사용하지 않고 추출과 인덱싱을 거쳐야 한다.
- Rule Inquiry는 답변만 저장하지 않고, 근거 상태와 후보 규칙을 함께 남긴다.
- Character Sheet는 Adventure와 결합되지만 별도 BC로 유지한다.
- Combat Map의 플레이어 이동과 AI 상태는 서로 다른 가시성 규칙을 가진다.

## 경계 메모

- AI Game Master는 도메인 상태를 따로 보관하는 애그리게이트가 없다.
- Adventure는 모든 모험 흐름의 중심 Aggregate이며, 저장과 재개, 삭제 상태까지 책임진다.
- Rule Knowledge는 업로드 처리와 검색 근거를 담당하고, 모험 진행 로직은 맡지 않는다.
