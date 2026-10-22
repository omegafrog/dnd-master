# 아키텍처 계약

## 배포와 통신의 기준

현재 코드는 Java 21·Spring Boot 기반 Gradle 다중 모듈이다. 주요 실행 서비스는 모험, 문서 지식, 캐릭터 관리, 전투 지도, AI 게임 마스터, 인증이며, `app-all`은 통합 실행 진입점이다. 사용자 PC 실행 경로에는 중계 서비스와 사용자 PC 에이전트가 있다. 각 서비스 경계는 `CONTEXT-MAP.md`의 현재 소유권을 따른다. 내부 런타임 기능을 별도 배포 서비스로 간주하지 않는다.

배포·실행 진입점은 [실행 안내](../agents/EXEC.md), [Gradle 설정](../../src/settings.gradle.kts), [CI](../../.github/workflows/dnd-master-ci.yml), [개발용 Compose](../../src/infra/compose.yaml)에 근거한다. 실제 운영 분리 방식은 배포 설정으로 확인된 범위만 기술하며, 통합 실행과 개별 서비스 부트스트랩이 함께 존재한다.

## 요청 흐름과 일관성

1. 모험 런타임은 현재 모험·시츄에이션·캐릭터 상태를 읽고 요청에 필요한 허가된 자료를 문서 지식 경계에 조회한다.
2. AI 게임 마스터는 상태와 근거를 입력받아 구조화 제안을 만든다. 모험 런타임은 제안의 구조·소유권·공개 범위를 검증한다.
3. 캐릭터 관리와 전투 지도는 각자의 상태 변경과 명령 이력을 소유한다. 호출자는 동일 요청 식별자로 재시도하며, 부분 실패는 소유 서비스의 기록과 복구 흐름으로 처리한다. 분산 서비스 간 단일 데이터베이스 트랜잭션을 가정하지 않는다.
4. 공개 응답은 권위 상태와 플레이어가 알 수 있는 정보에서 만든다. 숨겨진 지도·적·판정 정보는 전달 경계에서 제거한다.
5. AI 실행은 개발 시 로컬 실행, 배포 시 사용자 PC 실행기로 전달될 수 있다. 중계는 요청 사용자와 응답 상관관계를 유지한다.

## 내부 모델과 소유권

모험 런타임의 `AdventureSession`은 모험 상태를, `ScenarioPackage`는 준비된 시나리오 묶음을, `CombatEncounter`는 전투의 라운드·차례·행동 생명주기를 표현한다. 이 객체와 하위 모델은 `adventure-service` 안에 있으며 별도 서비스가 아니다. [모험 세션](../../src/adventure-service/src/main/java/com/dndmaster/adventure/domain/adventure/AdventureSession.java), [시나리오 묶음](../../src/adventure-service/src/main/java/com/dndmaster/adventure/domain/scenario/ScenarioPackage.java), [전투 조우](../../src/adventure-service/src/main/java/com/dndmaster/adventure/domain/combat/CombatEncounter.java).

`CharacterSheet`는 캐릭터 관리가 소유하고, `CombatMap`은 전투 지도 서비스가 소유한다. `RulebookIndex`와 인덱스 청크는 문서 지식 서비스가 소유한다. 각 저장 경계는 해당 서비스의 정본이며, 다른 서비스는 공개 API 또는 애플리케이션 포트를 사용한다. [캐릭터 시트](../../src/character-management-service/src/main/java/com/dndmaster/character/domain/CharacterSheet.java), [전투 지도](../../src/combat-map-service/src/main/java/com/dndmaster/combatmap/domain/CombatMap.java), [룰북 색인](../../src/rule-knowledge-service/src/main/java/com/dndmaster/ruleknowledge/domain/index/RulebookIndex.java).

상세 불변식과 구현 진입점은 각 소유 서비스 절에 기록한다. 이 초기 통합 계약은 클래스명만으로 확인되지 않은 세부 트랜잭션을 추정하지 않는다. 확인된 소유권은 [상황 지도](../../CONTEXT-MAP.md)를 따른다.

<a id="adventure-runtime"></a>
## 모험 런타임
수정일: 2026-10-22

### 설명
시나리오 준비, 모험 세션, 상황 선택, 전투 진행과 턴 확정을 소유하는 `adventure-service` 내부 경계다. 기능: [시츄에이션 기반 모험 진행](product.md#scenario-runtime), [전투 진행](product.md#combat-loop), [GM 입력](product.md#gm-context).

### 커뮤니케이션
- AI 게임 마스터에 입력 문맥과 허용된 실행 요청을 전달하고 제안을 받는다. 제안은 상태 적용 전에 검증한다.
- 문서 지식에 허가된 룰북·시나리오 근거를 조회한다.
- 캐릭터 관리와 전투 지도에 상태 변경 명령을 보내며 각 소유 서비스의 중복 방지·명령 이력에 맞춰 재시도한다.
- 서비스 간 정합성은 분산 트랜잭션이 아니라 식별 가능한 요청과 소유자 측 이력·복구로 유지한다. 근거: `CONTEXT-MAP.md` 모험 런타임 소유권 계약.

### 내부 구성요소
- 모험 세션과 전투 조우 모델은 이 경계의 정본이다. 실제 예시는 [AdventureSession](../../src/adventure-service/src/main/java/com/dndmaster/adventure/domain/adventure/AdventureSession.java), [CombatEncounter](../../src/adventure-service/src/main/java/com/dndmaster/adventure/domain/combat/CombatEncounter.java).
- [시나리오 준비](architecture.md#scenario-preparation)와 런타임 턴 조립은 같은 배포 모듈 내부의 별도 책임이다. 외부 서비스로 분리하지 않는다.
- 능력 상세 정의는 현재 모험 범위에 저장되고, 캐릭터별 능력 식별자와 연결된다.
- 모험 시작 때 선택한 룰북·판본·소유자에 맞는 적용 규칙 모음을 모험에 저장한다. 복구 때는 소유자와 예상 규칙 모음 식별자를 확인하고 기존 저장 자료를 재사용한다. 근거: [모험 세션 저장·복구](../../src/adventure-service/src/main/java/com/dndmaster/adventure/application/session/AdventureSessionApplicationService.java).
- 상황 진행 계약: 준비 API는 허용값 짧게/보통/길게 중 하나의 storyLength를 받아 세션에 저장·복원하고 초기 StoryRuntimeState에 전달한다. 필수 상황은 원문에서 명시한 isEnding 표시와 선행 그래프를 보존한다. RuntimeTurnApplicationService.situationSelectionContext는 활성 선택 상황 종료를 반영한 허용량이 남을 때 자연스럽게 이어지는 같은 단계 선택 상황과, 모든 명시적 선행 노드가 USED이고 아직 UNUSED인 필수 상황만 전체 단계에서 후보로 담는다. 입력은 `RuntimeGmInputBudget`의 실제 구역 배분을 따른다: 전체 80% 한도 안에서 고정 지침·잠긴 자료 최대 40%, 장기 기록 최대 10%, 압축 대화 최대 15%, 최근 대화 최대 20%, 최신 문맥은 실제 잔여량이다. 전체 단계의 선택 가능 후보 전체를 턴 문맥 JSON으로 직렬화한 크기는 8,192 UTF-8 byte 이내이며 후보 개수 자체에는 고정 상한을 두지 않는다. 후보 설명을 자르거나 조용히 생략하지 않는다. 전환 응답은 적격 준비 후보 선택만 허용하며 새 장면 전환 분기는 거부한다. 현재 활성 선택 상황의 종료·`USED` 전환과 사용량 증가는 원자적 턴 확정에서 한 번만 반영한다. 명시적 isEnding 필수 상황 하나가 USED로 확정되면 완료하며 다른 ending은 방문하지 않아도 된다. 필수 후보는 이미 USED인 경우 재선택할 수 없고, 합류 노드는 모든 명시적 선행 상황이 USED여야 한다. 선택한 길이·허용량·사용량은 StoryRuntimeState에, 증가 대기 상태는 PendingRuntimeState에 저장해 기존 원자적 확정과 복원 규칙을 따른다. `RuntimeGmPromptComposer`와 기존 조회·진행 경계를 사용하며 비공개 준비 내용을 플레이어에게 그대로 노출하지 않는다. 구현 완료로 표시하지 않는다.
- 탐험 중 위협 발견·공유는 모험 런타임의 `ExplorationCycle`이 캐릭터별 비공개 인지를 저장한다. GM의 `exploration-environment-response.json` 응답은 발견한 캐릭터 ID, 적이 먼저 알아챘는지, 양쪽이 모두 숨지 않았는지 반환하며 모험 런타임은 파티 소속 ID만 저장한다. 자연어 서술은 공개 응답에 포함되지만 발견자·인지자 ID는 포함하지 않는다. 명시적 알림은 같은 그룹의 발견자가 시작하며, 별도 GM 응답으로 적이 알림을 들었는지 판정한 뒤 인지를 그룹에 공유한다. 발견만으로 전투를 시작하지 않는다. 전투 전환 시 `CombatStartParticipantFactory`가 기존 `CombatEncounter` 참여자에 인지 여부를 적용하고, `CombatEncounter`는 인지하지 못한 참여자의 첫 차례를 건너뛴다. 이미 알고 있는 참여자의 차례에는 이 건너뛰기를 적용하지 않는다. 수치가 제공되지 않는 발견 판정은 숫자를 만들어내지 않고 GM 판정으로 남긴다.
- 적 시트 준비는 기존 `enemy-sheet-preparation-response.json`의 `전투수치` 계약을 사용한다. `HttpTypedRuntimeGmAgentPort`가 수치와 근거키를 역직렬화하고 제공된 자료에서 문서·버전·위치를 연결한다. `RuntimeTurnApplicationService`가 검증한 시트를 저장·재사용하고 전투 준비에 전달한다. 계약 단위 검증은 통과했으며 실제 전투 E2E 성공은 아직 확인하지 않았다.
- 일반 GM 턴의 근거 조회는 [#379 상황 선택·턴 조회·오케스트레이션 계획](https://github.com/omegafrog/dnd-master/issues/379)의 한 번의 혼합 자료 dense 검색과 출처별 최대 다섯 발췌를 사용한다. 이 경로에서는 별도 재정렬·충분성 판단 AI 요청을 추가하지 않는다. 다른 작업의 근거 정책과 구분한다.
- **전투 중 이동 사건 처리 (accepted-target; [UC-002 명세](../specs/combat-movement-spell-range/architecture-spec.md)):** 기존 `CombatEncounter`가 현재 플레이어 차례와 이동 예산을, Combat Map이 토큰 위치·실제 경로를 소유한다. 이동 사건이 발생하면 Adventure Runtime은 사건 사실과 서버 발급 작업·차례 식별자를 AI Game Master의 전용 고정 응답 계약으로 전달한다. 백엔드는 판정·공개 서술·필요한 플레이어 입력·전투 전환 결과를 구조로 검증하고, 확정 이동량·위치·턴 종료를 대신 결정하게 하지 않는다. 필요한 판정 뒤 실제 통과 위치를 확정하고 실제 이동량만 적용한 후 같은 플레이어의 명시적 다음 입력을 기다린다. 중단 경로의 나머지를 자동 실행하지 않는다. **현재 구현은 아직 이 목표를 충족하지 않는다:** 직접 전투 이동 응답은 일반 이동 완료 서술을 반환하고, 후속 연결은 적 발견 경로만 처리한다. 근거: [CombatActionApplicationService](../../src/adventure-service/src/main/java/com/dndmaster/adventure/application/combat/CombatActionApplicationService.java), [CombatMapRuntimeTurnCommandAdapter](../../src/adventure-service/src/main/java/com/dndmaster/adventure/application/runtime/CombatMapRuntimeTurnCommandAdapter.java), [ADR-018](../adr/ADR-018-staged-combat-map-movement-resolution.md).

### 배포
독립 `adventure-service` 부트스트랩과 `app-all` 통합 실행에 포함된다. 런타임 상태와 대화 기록을 소유한다. 분리 배포 시에도 캐릭터·지도 변경은 해당 소유 서비스 API를 통한다. 근거: `src/adventure-service`, `src/app-all`, `src/start-dev.sh`.

<a id="scenario-preparation"></a>
## 시나리오 준비
수정일: 2026-10-20

### 설명
`adventure-service` 안에서 문서·추출 결과를 참조해 플레이 가능한 시나리오 묶음을 준비한다. 이는 별도 배포 서비스가 아닌 모험 런타임 내부 경계다. 기능: [시츄에이션 기반 모험 진행](product.md#scenario-runtime).

### 필요 엔티티
- 입력: 모험 생성 요청, 선택된 문서와 추출 결과 식별자, 준비 설정.
- 조회: 허가된 문서 근거와 기존 준비 상태.
- 변경: 시나리오 묶음과 시츄에이션 준비 결과.
- GM 구조화 응답은 내부 식별자나 배열 위치 번호를 만들지 않는다. 출처는 서버가 전달한 허용 ID를 돌려주고, 필수 상황은 고유 요약과 요약으로 된 선행 관계를 돌려준다. 모험 런타임이 출처·단계·상황 ID를 부여하고, 요청 순서와 개수로 상세 결과를 대응시켜 관계를 복원한다. 필수 상황의 실행 순서는 원문 선행 관계 그래프만 제한한다.
- 승인된 내용 개선 목표는 기존 `SituationDefinition`에 이름(`title`, 120 Unicode 코드 포인트 이내)과 장면 설명(`description`, 2,000 이내)을 추가하는 것이다. 공백 제거 후 비어 있지 않아야 하며 내부 의도·관계·출처·지도 참조를 보존한다. `DetailedStage`의 기존 JSON 저장과 복원을 사용하고 새 테이블·과거 자료 이관·삭제를 추가하지 않는다.

### 동작
- 입력을 검증한 뒤 필요한 구조를 생성·검증해 모험 런타임의 준비 상태에 저장한다.
- 문서 내용 전체를 복제하는 대신 출처 참조와 필요한 파생 구조를 보존한다.
- 재시도는 같은 모험 식별자를 유지하며, 초기 단계 일부가 실패하면 게시하지 않고 전체 준비를 다시 수행한다. 구현 세부는 [ScenarioPreparationApplicationService](../../src/adventure-service/src/main/java/com/dndmaster/adventure/application/scenario/preparation/ScenarioPreparationApplicationService.java)에서 확인한다.
- 각 단계의 기존 `StageDetailedGenerationPort.Request`에는 해당 단계의 문제·연결 요약과 같은 준비 요청에서 이미 얻은 관련 검색 발췌를 전달한다. 발췌는 출처 범위를 검증한 뒤 합계 2,000 Unicode 코드 포인트로 제한한다. 초기 준비는 골격의 모든 단계를 시작 전에 생성·검증한 뒤 한 번에 게시하며, 이미 게시된 완성 자료가 있으면 재사용한다. 부분 준비 실패는 임시 결과를 폐기하고 같은 모험에서 모든 단계를 다시 생성·검증한다. 실패한 단계부터 이어 생성하지 않는다. 원문 재검색이나 영속 발췌 캐시는 추가하지 않는다.
- 생성 스키마·프롬프트 및 `validatePreparedStage`·`validateInitialSituationSet`에서 중간 후보 최소 두 개 조건을 제거하고 후보 수 자체는 제한하지 않으며 선택 가능 후보 문맥 JSON에 8,192 UTF-8 byte 한도를 적용한다. 초과 응답 JSON을 한 차례 보정 요청 입력으로 재사용해 필수 상황을 보존한다. 재초과하면 준비를 실패시킨다. 시작 상황·필수 상황·원문 선행 관계와 제공 지도 검증은 유지한다. 형식 검증만으로 장면의 서사 적합성을 증명하지 않으며 실제 모델의 생성 결과를 별도 확인한다.
- 초기 준비 게시와 실제 시작 경계에서 필수 opening의 미완료 선행 필수 상황을 검증한다. 새 모험에는 완료된 필수 상황이 없으므로 선행 상황이 있는 필수 opening은 거부하고, 시작 상태나 현재 상황을 저장하지 않는다. 같은 모험에서 맵 파일의 이미지 식별값이 저장된 격자 조정이 있는 맵과 같으면 기존 준비 화면에서 재사용 또는 직접 수정을 먼저 묻는다. 재사용은 대상 맵에 저장된 격자 조정을 그대로 적용하고, 직접 수정은 기존 격자 편집 흐름을 사용한다.

### 메서드
- `prepareScenario`: 준비 요청을 받아 문서 식별자를 확인하고 시나리오 묶음 생성·검증을 조립한다. 도메인 판단은 해당 소유 모델과 검증기로 위임한다.

<a id="document-knowledge"></a>
## 문서 지식
수정일: 2026-10-19

### 설명
`CONTEXT-MAP.md`에서 Document Knowledge라 부르는 경계로, 룰북·시나리오 자료의 등록·추출·색인·근거 검색을 제공하는 `rule-knowledge-service`다. 기능: [룰북 근거 검색](product.md#rule-evidence), 폐기된 검색 설계의 기록 보관.

### 커뮤니케이션
- 모험 런타임과 검색 요청·근거 위치를 주고받는다. 허가 범위와 문서 식별자를 조회 조건에 포함한다.
- 저장소와 벡터 검색은 문서 지식 소유 서비스가 관리한다. 검색 실패는 빈 근거로 구분해 호출자에게 돌려준다.
- 검색 순위는 근거 충분성 자체를 대신하지 않으며, 최종 적용 판단은 소비자 경계의 계약에 따른다.

### 내부 구성요소
- 룰북 등록·추출, 색인 생성, 검색 애플리케이션과 `RulebookIndex`, `EmbeddedRulebookChunk` 모델. 근거: [RulebookIndex](../../src/rule-knowledge-service/src/main/java/com/dndmaster/ruleknowledge/domain/index/RulebookIndex.java), `src/rule-knowledge-service/src/main/java/com/dndmaster/ruleknowledge/application/`.

### 배포
독립 `rule-knowledge-service` 부트스트랩이 있으며 `app-all` 실행에도 포함된다. 벡터 확장 데이터베이스는 개발 Compose에 정의된다. 근거: `src/rule-knowledge-service`, `src/infra/compose.yaml`.

<a id="character-management"></a>
## 캐릭터 관리
수정일: 2026-10-19

### 설명
캐릭터 시트와 캐릭터별 정본 값 변경을 소유하는 `character-management-service`다. 기능: [캐릭터 시트 관리](product.md#character-sheet), [주문·기술 준비](product.md#ability-use).

### 커뮤니케이션
- 모험 런타임에서 캐릭터와 승인된 상태 변경 명령을 받는다.
- 캐릭터의 정본 시트와 파생 값을 소유 경계에서 검증한다. 모험 런타임은 시트 전체를 복제해 정본으로 취급하지 않는다.
- 실패·중복 요청은 소유 서비스 명령 이력과 재시도 계약을 따른다.

### 내부 구성요소
- `CharacterSheet`와 시트 변경 응용 흐름. 근거: [CharacterSheet](../../src/character-management-service/src/main/java/com/dndmaster/character/domain/CharacterSheet.java), `src/character-management-service/src/main/java/com/dndmaster/character/application/`.

### 배포
독립 `character-management-service` 부트스트랩 및 `app-all` 통합 실행에 포함된다. 캐릭터 저장소와 상태 변경을 소유한다. 근거: `src/character-management-service`, `src/app-all`.

<a id="combat-map"></a>
## 전투 지도
수정일: 2026-10-22

### 설명
맵, 공간 요소, 토큰 이동, 전장의 안개와 플레이어 공개 정보를 소유하는 `combat-map-service`다. 기능: [전장 지도 준비](product.md#map-runtime), [지도 이동과 조우](product.md#spatial-movement).

### 커뮤니케이션
- 모험 런타임이 지도 준비·활성화와 이동 명령을 보낸다.
- 소유 서비스가 이동 경로와 공간 상태를 검증하고 공개 가능한 지도 투영을 반환한다.
- 요청 실패나 재시도는 이동 명령 식별자와 소유자 측 작업 상태를 통해 중복 적용을 막는다.

### 내부 구성요소
- `CombatMap`과 이동 해결 흐름. 모험·소유자에 연결한 지도 정의는 바꾸지 않으며, 같은 정의의 저장된 지도를 재사용한다. 첫 진입에서만 시작 배치를 해결하고 재방문은 저장된 지도 상태를 유지한다. 시작 위치 후보 생성은 후보가 없거나 제공자 요청에 실패하면 최초 요청을 포함해 최대 6회 시도하며, 소진하면 수동 배치 경로를 제공한다. 같은 모험 안에서 동일한 맵 파일에 저장 격자가 있으면 준비 화면에서 사용자에게 재사용 또는 직접 수정을 먼저 묻는다. 재사용은 기존 정렬값을 유지해 대상 지도에 적용하며, 직접 수정은 기존 편집기와 저장 경계를 사용한다. 격자 보정은 [CombatMapViewService.calibrateGrid](../../src/combat-map-service/src/main/java/com/dndmaster/combatmap/application/view/CombatMapViewService.java)의 별도 경로에서 가로·세로 칸 수 변경을 검증한다. 변경 시 새 범위 밖 콘텐츠는 거부하고, 이전 칸 좌표 기준의 `MAP_BOUNDARIES` 레이어는 제거해 다시 그리게 한다. 정렬 전용 API는 칸 수를 변경하지 않는다. 근거: [CombatMapViewService](../../src/combat-map-service/src/main/java/com/dndmaster/combatmap/application/view/CombatMapViewService.java), [CombatMap](../../src/combat-map-service/src/main/java/com/dndmaster/combatmap/domain/CombatMap.java), `src/combat-map-service/src/main/java/com/dndmaster/combatmap/application/movement/`.
- **전투 이동 중단 (accepted-target; [전투 이동 설계 상태](../specs/combat-movement-spell-range/diagrams/architecture/combat-movement-interruption.state.svg)):** 기존 장기 실행 이동 예약에서 경로를 칸 순서로 검증하고 GM 처리가 필요한 사건 칸에서 멈춘다. 필요한 판정이 끝나면 실제 통과 경로의 최종 위치·공간 상태를 기존 저장 경계에서 한 번 확정한다. 칸별 검증은 각 칸을 데이터베이스에 따로 확정한다는 뜻이 아니다. 현재 이동 서비스는 중단 사실과 실제 도달 경로를 반환하지만, 일반 전투 행동 경로에는 이 결과를 GM 처리에 연결하는 목표 동작이 아직 구현되지 않았다. 기존 지도 소유권과 ADR-018 예약/복구 규칙을 유지하며 새 경계나 서비스를 만들지 않는다.

### 배포
독립 `combat-map-service` 부트스트랩 및 `app-all` 통합 실행에 포함된다. 지도와 이동 상태를 소유한다. 근거: `src/combat-map-service`, `src/app-all`.

<a id="ai-game-master"></a>
## AI 게임 마스터
수정일: 2026-10-19

### 설명
모험 런타임이 구성한 입력을 바탕으로 GM 서술·판정·구조화 제안을 반환하는 `ai-game-master-service`다. 기능: [시츄에이션 진행](product.md#scenario-runtime), [GM 입력](product.md#gm-context), [전투 행동](product.md#combat-loop).

### 커뮤니케이션
- 모험 런타임에서 요청을 받아 결과를 반환한다. AI 응답은 제안이며 정본 상태 적용 권한을 갖지 않는다.
- 실행 포트에서 개발용 로컬 Codex 또는 원격 사용자 PC 실행기를 사용한다.
- 출력 구조·정보 공개·근거 범위 검사는 호출자인 모험 런타임이 적용 전에 수행한다.

### 내부 구성요소
- 프롬프트 구성, 공급자 실행 어댑터, 구조 응답 해석과 진단. 관련 소스는 `src/ai-game-master-service/src/main/java/` 및 `src/ai-game-master-local-codex/`다.

### 배포
독립 `ai-game-master-service`와 `app-all` 통합 실행이 있다. 로컬 Codex 어댑터는 개발 실행 경로다. 근거: `src/ai-game-master-service`, `src/ai-game-master-local-codex`.

<a id="identity-access"></a>
## 사용자 인증
수정일: 2026-10-19

### 설명
사용자 인증과 세션 신원 확인을 소유하는 `identity-access-service`다. 기능: [AI 제공자 연결](product.md#ai-provider).

### 커뮤니케이션
- 애플리케이션 서비스가 인증된 사용자 식별을 검증한다.
- 사용자별 AI 연결 자격 정보는 공개 응답·진단 로그에 노출하지 않는다.
- 인증 실패는 요청을 거부하며 다른 사용자의 세션이나 자격 정보를 조회하지 않는다.

### 내부 구성요소
- 인증 API, 인증된 주체 해석기, 세션 조회 응용 흐름. 근거: `src/identity-access-service/src/main/java/`.

### 배포
독립 `identity-access-service`와 `app-all` 통합 실행에 포함된다. 사용자 인증과 연결 권한을 소유한다. 근거: `src/identity-access-service`, `src/app-all`.

<a id="agent-relay"></a>
## 사용자 PC 실행 중계
수정일: 2026-10-19

### 설명
원격 AI 실행 요청을 인증된 사용자 PC 에이전트와 연결하는 중계 경계다. 기능: [사용자 PC AI 요청 실행](product.md#remote-agent).

### 커뮤니케이션
- AI 실행 서버와 사용자 PC 에이전트가 WebSocket 연결을 사용한다.
- 요청 식별자와 사용자 식별자를 유지해 응답을 원 호출자에게 전달한다.
- 연결 단절·시간 초과는 명시적 실패로 돌려보내고 모험 상태 변경은 호출자 확정 전에 하지 않는다.

### 내부 구성요소
- 중계 서비스, 연결 등록과 요청 전달. 근거: `src/agent-connection-relay-service/src/main/java/` 및 `src/user-pc-agent/`.

### 배포
중계 서비스와 사용자 PC 에이전트는 별도 실행 경계다. 로컬 Codex 실행 경로와 혼동하지 않는다. 근거: `src/agent-connection-relay-service`, `src/user-pc-agent`.
