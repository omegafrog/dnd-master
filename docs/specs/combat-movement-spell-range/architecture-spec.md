# 전투 이동·주문 사거리 Architecture Spec

- 범위 ID: `combat-movement-spell-range`
- 문서 상태: **READY** — 요구사항·아키텍처 결정, 다이어그램·링크 검토 완료; 애플리케이션 테스트는 수행하지 않음
- 입력: [Product Spec](product-spec.md), `CONTEXT.md`, `CONTEXT-MAP.md`, ADR-001, ADR-003, ADR-012, ADR-013, ADR-018, ADR-019
- Engineering Decision metadata: 이 작업은 승인된 ID 기반 decision workflow opt-in이 아니므로 decision ID, 승인 ID, Principle/Evidence ID를 만들지 않는다.
# 1. Design Scope

## 1.1 Target
| 항목 | 대상 |
|---|---|
| Product Spec | `docs/specs/combat-movement-spell-range/product-spec.md` |
| Use Cases | UC-001 준비, UC-002 이동·주문, UC-003 지도 확대, UC-004 주문 효과의 환경 응용 |
| Domain | 주문·행동 정의, 선택·미리보기, 전투 실행, 환경 상호작용 |
| Bounded Contexts | Scenario Preparation, Adventure Runtime, Document Knowledge, AI Game Master, Combat Map, Character Management, Dice Roll — 모두 기존 경계 |
| Existing Services | `adventure-service`, `rule-knowledge-service`, `ai-game-master-service`, `combat-map-service`, `character-management-service`, `dice-roll-service`, `web-ui` |
| External Dependencies | 기존 HTTP 서비스 경계와 현재 AI 제공자 연동. MCP 서버 추가 없음 |
| Affected Data | 게시 원본 정의·출처 버전, ScenarioPackage의 모험별 정의·편집·승인, Bundle 잠금 참조, 전투 명령·실행 단계 |
## 1.2 Product Spec Mapping
| Product 항목 | 아키텍처 매핑 |
|---|---|
| UC-001, BR-01~04, BR-22, BR-29 | Document Knowledge의 불변 게시 원본과 Scenario Preparation의 전체 주문 정의·모험별 수정본·승인본; Bundle Lock |
| UC-002, BR-05~21 | Adventure Runtime의 전투 명령·규칙 판정; Combat Map의 이동·위치·고도·시야; Character Management와 Dice Roll의 기존 권위 |
| UC-004, BR-23~28 | AI Game Master가 허용 목록으로 환경 응용을 계획; Adventure Runtime의 `OfficialGmToolRegistry`/`GmToolGatewayService`와 기존 명령 경계가 검증·실행 |
| AC-14~15, BR-14~15 | 공개 정보만 사용한 읽기 전용 미리보기; 현재 encounter/map/definition 버전 최종 검증 |
| AC-21, BR-22 | 구조·출처·지원 특성의 자동 확인과 의미·구성의 사용자 최종 검토를 분리 |
| AC-23~26, BR-24~28 | 기본 주문 효과·비용을 한 번 확정한 뒤 추가 환경 응용 오류를 독립 처리; 오류를 GM에 반환해 수정·재시도 |
# 2. Domain Flow and Hotspots

## 2.1 핵심 흐름

- **준비:** 게시 룰북 원본·추출 버전을 참조해 D&D 5판(2014) 기본 룰북 전체 주문 및 선택 추가 자료를 ScenarioPackage에 구조화한다. 구조, 출처, 지원 가능한 특성만 자동 검사한다. 사용자가 의미·수치·조건·구성을 직접 검토·수정·승인한다. 수정본은 현재 모험에만 적용하고 시작 전에 잠근다.

`combat-01`의 기본 주문 목록은 제공된 `DnD_BasicRules_2018.pdf`와 동반 노드 자료에서만 만든다. 각 정의는 선택된 기본 룰북의 실제 문서 ID·추출 버전과 PDF 페이지·노드 위치를 보존한다. 이 인벤토리에 선택 추가 자료를 섞지 않는다.
- **이동·주문:** 공개 상태 미리보기 → 사용자 선택/명시적 제출 → 기대 encounter/map/definition 버전 확인 → 규칙 검증과 기존 명령 실행. 시전 시간은 정의된 규칙을 따르며, 모든 주문이 즉시 클릭 한 번으로 발동하는 것은 아니다.
- **환경 응용:** 기본 주문 효과와 비용을 한 번 확정 → GM에게 지원 작업·입력 스키마·제약 전달 → 허용 목록 내에서 계획·호출 → 성공 효과 반영 또는 오류를 GM에 반환해 입력/작업을 보정. 기본 주문 결과는 추가 응용 실패로 되돌리지 않는다.
## 2.2 명령·결과·정책
| 명령/결과 | 소유자 | 결과 |
|---|---|---|
| 정의 검토·수정·승인 | Scenario Preparation / 사용자 | 출처 추적 가능한 모험별 승인 정의; 모험 시작 전에 Bundle Lock |
| Spell Preview | Adventure Runtime | 공개된 범위·대상·비용·요구 선택; 어떠한 상태·자원 변경도 없음 |
| Spell Execute | Adventure Runtime / `CombatEncounter` | 멱등 명령 결과, 기본 효과·비용 한 번 반영 |
| 환경 작업 요청/오류 | `GmAgentRuntimePlanningAdapter`, 기존 GM 도구 게이트웨이 | 권한·지원·스키마 검사 후 명령 결과; 실패한 호출·구조화 오류·현재 허용 목록과 제약은 수정 정보로 GM에 반환 |
| 이동 Preview/Operation | Combat Map + Adventure Runtime | 지도 경로·비용 미리보기; 실제 도달 단계만 이동량으로 반영 |
## 2.3 Hotspots

- 기본 룰북 전체 범위는 이름을 대상 후보로 나열하는 것으로 충족되지 않는다. 각 주문의 실제 판정·효과·지속·발동을 엔진이 처리해야 한다. 비전투, 의식, 긴 시전, 소환·조종, 지속 발동 및 수치·특성 조합도 포함한다.
- 기본 주문 결과는 특성별 엔진 처리로 결정론적으로 확정한다. 상황별 사실을 판단하거나 판정이 필요한 환경 응용은 기존 GM/플레이어 굴림 경계를 쓸 수 있으나, 기본 주문 규칙의 미구현 대체 수단이 아니다. 임의 AI 상태 변경·코드는 허용하지 않는다.
- 수치 응답 목표는 정해지지 않았다(`system-targets.yaml`의 ST-005 unresolved); 성능 SLA를 이 문서에서 만들지 않는다.
# 3. DDD Architecture

## 3.1 Bounded Contexts and Boundary Decisions

새 Bounded Context, 배포 서비스, 별도 모듈을 만들지 않는다. 기능은 아래 기존 소유 경계 안에서 확장한다.
| Capability | Owner Context | Chosen Boundary | 더 약한 경계가 부족한가? | 더 강한 경계 비용 |
|---|---|---|---|---|
| 주문 정의·특성 실행 | Adventure Runtime | 기존 `adventure-service` 내부 도메인/응용 기능 | 기존 `CombatRulesEngine`와 명령 흐름을 확장해 불변식·멱등성을 보존 | 새 서비스는 공유 상태 조정과 동기 호출을 늘리며 독립 수명주기 근거가 없음 |
| 모험별 구조화·검토·승인 | Scenario Preparation | 기존 `adventure-service` 패키지·ScenarioPackage 저장 | ScenarioPackage가 이미 모험 자료·수정본·리비전을 소유 | 별도 컨텍스트·저장소는 Bundle Lock 전후 불일치 추가 |
| 이동·고도·벽·시야·공간 효과 | Combat Map | 기존 `combat-map-service` | 지도 상태와 칸별 이동의 기존 정본을 재사용 | 지도 로직 복제 시 가시성·경로 권위 충돌 |
| GM 환경 응용 허용 목록 | Adventure Runtime / AI Game Master | 기존 도구 등록·게이트웨이·GM 계획 연동 | 기존 GM 호출과 권한 발급 경계에 기능을 추가 | MCP/새 외부 서버는 요청되지 않았고 운영 경계만 추가 |
## 3.2 Context Map and Ownership
| Context | 책임 및 소유 자료 |
|---|---|
| Document Knowledge | 게시된 기본 룰북/추가 자료의 불변 원본, Source Span 및 추출 버전 |
| Scenario Preparation | 전체 기본 주문 인벤토리와 선택 자료 정의의 모험별 구조화·수정·승인. Bundle Lock은 승인 정의·출처·추출 버전을 고정 |
| Adventure Runtime | `CombatEncounter`, 턴·행동 자원·주문 슬롯/집중 사용·명령 실행과 결과; 기존 서비스 명령 조정 |
| Combat Map | 위치·지면으로부터의 높이·경계선·지형·점유·시야·탐험·공간 효과 및 이동 중 실제 도달 단계 |
| Character Management / Dice Roll | 각자의 기존 캐릭터·자원 정본 및 굴림 결과 |
| AI Game Master | 제한된 근거와 허용 작업 목록으로 제안/작업 요청. 규칙·상태를 직접 확정하지 않음 |
Scenario Preparation은 Document Knowledge의 문서·추출 버전을 ID로 참조하고 원본을 수정하지 않는다. Adventure Runtime은 AI 제안을 검증하고 Combat Map, Character Management, Dice Roll에 각 소유자 명령을 보낸다. 상태 복제나 분산 ACID 트랜잭션은 도입하지 않는다.
## 3.3 Aggregates, Types, and Invariants
| Aggregate/개념 | 책임 및 불변식 |
|---|---|
| `ScenarioPackage` | 주문 정의 전체 인벤토리·모험별 편집본·사용자 승인 이력을 보유. 승인본은 현재 모험에만 적용; 공개 룰북/다른 모험 자료는 변경하지 않음 |
| `Bundle` | 기존 Bundle Lock으로 정확한 ScenarioPackage·정의·원문·추출 버전을 참조 |
| `CombatEncounter` | Initiative, 턴, 캐릭터와 현재 속도 변경 효과에서 계산한 속도/이동 사용량, `TurnResources`의 행동·보너스 행동·반응 및 주문 슬롯·집중 사용, 확정 전투 결과 소유 |
| 기존 Runtime Command operation | commandId·단계·결과의 영속 상태. 재실행 방지 및 불명 결과 조회 |
| `SpellActionDefinition` | 안정 ID·버전·출처, 시전 시점/비용/구성요소, 사거리·대상·형상, 경로·엄폐 조건, 지속/집중/발동, 효과·단계별 변화, 특성 매개변수 |
| `SpellActionSelection` | 선택된 정의 버전, 슬롯, 대상·배분, 위치/방향/고도, 기대 상태 버전 |
| `SpellActionPreview` | 공개 가능한 사거리/효과/경로/비용/대상/선택 필요/경고만 포함 |
| `SpellTrait` | 서로 다른 실제 규칙 기제를 구현하는 전략. 정의·선택·문맥을 평가해 허용된 연산과 추가 요구조건 반환 |
`CombatEncounter`는 턴의 행동·슬롯·집중 사용을 조정하며 Character Management의 캐릭터 상태 권위를 대체하지 않는다. Character Management는 자신의 자원·HP·효과 상태를 계속 소유한다. Combat Map은 위치와 지도 효과를 소유한다. Dice Roll은 주사위 결과를 소유한다.
## 3.4 Business Rule Ownership and State
| 규칙 | 소유/강제 지점 |
|---|---|
| 기본 룰북 모든 주문 지원, 출처·버전 일치 | ScenarioPackage 준비 검사 및 `SpellActionRulesEngine`; 의미 최종 검토는 사용자 |
| 추가 자료 미지원/필수 차단 | Scenario Preparation의 사용 가능성·필수성 검사; 선택 미지원은 경고 확인, 필수면 시작 차단 |
| 사거리·가시성·명확한 경로·엄폐·확산 분리 | `SpellActionRulesEngine`과 Combat Map의 권위 질의 |
| 현재 턴·행동·자원·집중과 실행 순서 | `CombatEncounter`, `CombatRulesEngine`, Character Management |
| 이동 비용·실제 중단 위치·고도·벽 공개 | Combat Map이 단계별 경로/위치·공개를 확정하고 Adventure Runtime이 전투 이동량 반영 |
| AI 작업은 허용 목록·권한·입력 조건을 통과해야 함 | `GmToolGatewayService` preflight/invoke 및 기존 runtime command 경계 |
| 기본 효과/비용은 환경 응용 실패나 재시도에 중복되지 않음 | 기존 영속 Runtime Command 단계 |
| 전이 | 조건/결과 |
|---|---|
| 초안 → 사용자 검토 → 승인 | 구조·출처·지원 특성 확인 후 모험 시작 전에 사람이 검토·수정·승인 |
| 승인 → 잠금 | 현재 모험 전용 승인 정의와 원문/추출 버전을 기존 Bundle Lock에 고정 |
| 미리보기 → 제출/거부 | 공개 상태만 읽음; 오래되었거나 미지원이면 비용 없이 거부 |
| 제출 → 실행 → 기본 확정 | 기존 operation/명령으로 기본 주문 효과와 비용 한 번 반영 |
| 기본 확정 → 환경 응용 | 의도가 있을 때만 허용 작업 확인과 선택적 굴림 후 호출 |
| 호출 실패 → 보정 → 재시도/추가 실패 | 오류 내용을 GM에 돌려주며 기본 주문 효과/비용은 재실행하지 않음; 횟수 상한 없음 |
| 이동 실행 → 중단 | 실제 도달한 칸과 그때까지 비용만 반영 |
업무 상태와 구별되는 실행 수명주기는 [설계 상태 다이어그램](diagrams/architecture/spell-action-execution.state.svg)에 있다. 구조 모델은 [클래스 다이어그램](diagrams/architecture/spell-action.class.svg)을 참조한다.
# 4. Program Design

## 4.1 Responsibilities and Target Types
| Component/type | 경계 | 책임 |
|---|---|---|
| `CombatActionApplicationService` | 기존 application service 확장 | 주문과 일반 행동 요청을 기존 encounter/Runtime Command/character/dice/map 명령으로 조정 |
| `CombatRulesEngine` | 기존 domain engine 확장 | 턴·행동·자원 선행 조건과 제안 검증 |
| `SpellActionRulesEngine` | 기존 `adventure-service` 내부 구성요소 | 정의·특성 평가; 공개 미리보기와 권위 실행을 분리 |
| `SpellTrait` / `SpellActionTraitRegistry` | 같은 combat domain package | 서로 다른 기제의 실행 전략과 등록된 특성 조합 |
| `ScenarioPreparationApplicationService` | 기존 준비 경계 확장 | 주문 정의 구조화, 사용자 수정/승인, 패키지 버전 고정 |
| `GmAgentRuntimePlanningAdapter` | 기존 AI 계획 어댑터 확장 | 실제 HTTP/local 계획 요청 payload에 허용 작업 이름·스키마·제약 포함; typed tool call을 파싱하고 실패 호출·구조화 오류·현재 허용 목록을 수정 요청에 전달 |
| `OfficialGmToolRegistry`, `GmToolGatewayService` | 기존 allowlist/gateway 확장 | 선언된 작업 목록, 권한·스키마 preflight, 호출/결과 조회 |
| `CombatController`, `AdventureController`, `ScenarioPreparationController` | 기존 API 경계 확장 | 기존 전투·지도·준비 경로에 요청/응답 계약 추가 |
| `CombatScreen.tsx`, `CombatMapView.tsx` | 기존 UI | 선택·가리킴·미리보기·확정, 대상 배분, 고도, 확대 변환 정합 |
## 4.2 Contracts and Signatures

```text
SpellTrait.evaluate(definition, selection, context) -> TraitEvaluation
SpellActionRulesEngine.preview(publicContext, definition, selection) -> SpellActionPreview
SpellActionRulesEngine.execute(authoritativeContext, definition, selection, commandId) -> 기존 영속 명령 결과 (existing durable command result)
```
`TraitEvaluation`은 허용된 타입 연산, 추가 사용자 선택/굴림 요구, 거부 사유를 반환한다. 특성은 복수의 실제 주문 기제를 구현한다. 주문별 숫자와 특성 조합은 버전형 데이터이며, 새 기제를 추가할 때만 코드를 추가한다. 필수 구조·형식·지원 특성은 자동 검사한다. trait 조합의 의미·조건 충돌은 사용자가 검토하며 완전한 의미 충돌 검증기를 요구하지 않는다. 임의 스크립트는 실행하지 않는다.

전투 화면의 입력 흐름은 선택 → 가리키기 → 미리보기 → 확정이다. 단일 대상 선택만으로 충분하면 클릭으로 제출한다. 여러 대상이나 투사체 배분이 필요하면 선택을 마친 뒤 명시적으로 시전한다. 원뿔·선처럼 방향이 규칙상 필요한 효과는 시전자에서 커서 방향을 사용하고, 점 지정 효과는 커서를 중심점으로 삼는다. 마법 화살처럼 유효 대상 지정형 주문에는 임의 방향을 요구하지 않는다. 지면으로부터의 높이는 비행·거리·효과 판정에서 필요한 경우에만 입력·표시한다.
### API contracts
| 기존 및 추가 경로 | 계약 |
|---|---|
| `GET /api/v1/scenario-packages/{scenarioPackageId}/play-preparation` | 정의 원문 출처·특성·수치·조건·지원 상태와 사용자 검토/승인 상태 반환 |
| `PUT /api/v1/scenario-packages/{scenarioPackageId}/play-preparation/spell-action-definitions/{definitionId}` | `expectedRevision`과 편집본 수신. 패키지 소유권·버전 검사, 원문/편집 출처 보존 |
| `POST /api/v1/scenario-packages/{scenarioPackageId}/play-preparation/spell-action-definitions/{definitionId}/approve` | 모험 시작 전 사용자 승인; 현재 패키지/Bundle에만 고정 |
| `POST /api/v1/adventures/{adventureId}/combat/actions/preview` | 일반 행동 및 주문의 선택과 위치/방향/높이, `commandId`, expected encounter/map/definition versions. 공개 투영만 반환하고 상태를 바꾸지 않음 |
| `POST /api/v1/adventures/{adventureId}/combat/spells/preview` | 주문 `definitionId/version`, 슬롯·대상/배분·필요 시 위치/방향/높이, `commandId`, expected encounter/map/definition versions. 공개 투영만 반환하고 상태를 바꾸지 않음 |
| 기존 `POST /api/v1/adventures/{adventureId}/combat/actions` | 일반 행동 제출; `Idempotency-Key`, `If-Match-Version`과 최신 선택 정보를 기존 멱등 경로에서 검증 |
| 기존 `POST /api/v1/adventures/{adventureId}/combat/spells` | 이름 대신 잠긴 `definitionId/version` 기반 선택; `Idempotency-Key`, `If-Match-Version`, 필요한 대상·배분·방향·높이 포함 |
| 기존 `POST /api/v1/adventures/{adventureId}/combat-map/movement-preview` 및 이동 제출 경로 | 현재 속도·남은 이동량·지형 비용·고도·공개 가능한 장애물/도달 영역; 미리보기와 실행에 `commandId` 및 기대 지도/전투 버전을 적용 |
정의 버전·기대 버전 불일치, 미지원 특성, 무효 대상/선택, 비용 부족은 명시적 오류로 반환한다. 추가 GM 작업 오류는 분류 가능한 오류 내용과 보정 단서를 GM에 돌려준다. 기존 단일 `spellName` 입력은 잠긴 정의가 유일하게 결정될 때만 호환 어댑터로 수용하고, 모호하거나 미지원이면 거부한다. 클라이언트가 신뢰할 사거리·피해·방어도 값을 보내지 않는다.
## 4.3 Dependency Rules

- 응용 계층은 기존 domain types/rules와 소유자 포트만 호출한다. UI와 GM은 권위 상태를 직접 변경하지 않는다.
- `SpellActionRulesEngine`은 AI 제공자, HTTP 컨트롤러, 저장소 구현에 의존하지 않는다. 실행 영속화는 기존 `CombatActionApplicationService`/operation 및 Runtime Command 경계를 재사용한다.
- Combat Map 계산은 Adventure Runtime에서 복제하지 않는다. Character/Dice/Map 정본을 복제하지 않는다.
- GM은 `OfficialGmToolRegistry`의 실제 허용 작업과 형식만 계획하며, `GmToolGatewayService`의 권한·입력 검사를 우회할 수 없다.
# 5. Technical Architecture

## 5.1 Boundary Mapping
| Bounded Context | Capability | Code boundary | 배포 단위 | 근거 |
|---|---|---|---|---|
| Scenario Preparation | 정의 준비·승인 | 기존 scenario package 영역 | `adventure-service` | 현재 모험 package 수명주기와 일치 |
| Adventure Runtime | 주문/행동 실행, 전투 조정 | 기존 `application.combat`, `domain.combat`, runtime | `adventure-service` | CombatEncounter·operation을 이미 소유 |
| AI Game Master | 계획/보정 | 기존 planning adapter와 provider 계약 | `ai-game-master-service` 기존 호출 | 추론은 제안만, 저장 권한 없음 |
| Combat Map | 지도 조회/이동/시야 | 기존 map API·application | `combat-map-service` | 위치·경계·공개 정본 |
## 5.2 Current Gap and File Change Map

현재 `CombatActionApplicationService.castSpell(..., spellName)`은 하드코딩된 마법 화살 한 종류를 처리하고 일반 행동/주문 정의 미리보기·선택 계약이 없다. 기존 이동 미리보기에는 이 제품의 고도·전 범위 주문 규칙 계약이 없다. `GmAgentRuntimePlanningAdapter`가 허용 작업을 얻어도 현재 `HttpTypedRuntimeGmAgentPort.plan(context, tools)`는 `tools`와 capability를 무시하고 `plan(context)`로 위임한다. 따라서 변경 목표는 HTTP 요청 본문과 local AI 계획 입력 모두에 허용 작업 이름·입력 스키마·제약을 넣고, 응답에서 타입이 있는 작업 호출을 해석하는 것이다. 기존 MCP 연동은 룰북 검색 전용 읽기 경로로 유지한다.
| 경로 | 변경 제안 |
|---|---|
| 기존 `src/adventure-service/src/main/java/com/dndmaster/adventure/domain/combat/CombatRulesEngine.java`; 같은 디렉터리에 추가할 자료형 | `SpellActionDefinition.java`, `SpellActionSelection.java`, `SpellActionPreview.java`, `SpellTrait.java`, `SpellActionTraitRegistry.java`, `SpellActionRulesEngine.java` 추가; 기존 규칙 판정 확장 |
| 기존 `src/adventure-service/src/main/java/com/dndmaster/adventure/application/combat/CombatActionApplicationService.java` | 일반 행동·주문 미리보기/실행을 기존 Runtime Command/영속 operation 경로에 연결 |
| 기존 `src/adventure-service/src/main/java/com/dndmaster/adventure/application/scenario/preparation/ScenarioPreparationApplicationService.java`, `src/adventure-service/src/main/java/com/dndmaster/adventure/domain/scenario/ScenarioPackage.java`, `src/adventure-service/src/main/java/com/dndmaster/adventure/infrastructure/persistence/PostgresScenarioPackageRepository.java` | 구조 확인·사용자 수정·승인, 현재 모험용 정의와 버전 저장 |
| 기존 `src/adventure-service/src/main/java/com/dndmaster/adventure/application/runtime/GmAgentRuntimePlanningAdapter.java` | 허용된 도구 목록/기능 제약을 계획·보정 단계에서 전달 |
| 기존 `src/adventure-service/src/main/java/com/dndmaster/adventure/application/runtime/OfficialGmToolRegistry.java`, `GmToolGatewayService.java` | 현재 도구 이름/스키마를 제공하고 실행 전 권한·입력 검증 |
| 기존 `src/adventure-service/src/main/java/com/dndmaster/adventure/infrastructure/integration/HttpTypedRuntimeGmAgentPort.java` | HTTP AI 요청에 도구와 제약을 실제 포함하고 typed tool call을 해석; repair에 실패 호출·구조화 오류·현재 허용 목록 전달 |
| 기존 `src/adventure-service/src/main/java/com/dndmaster/adventure/api/CombatController.java`, `AdventureController.java`, `ScenarioPreparationController.java` | 기존 경로의 요청 DTO·응답 계약 확장 |
| 기존 `src/adventure-service/src/main/java/com/dndmaster/adventure/infrastructure/persistence/PostgresCombatActionOperationRepository.java` | 영속 Runtime Command operation 단계/재조정 상태 저장 확장 |
| 기존 `src/combat-map-service/src/main/java/com/dndmaster/combatmap/api/CombatMapController.java` 및 기존 이동/view 도메인 | 고도와 공개 정보 기준의 이동·기하 질의 확장 |
| `src/web-ui/src/features/combat/CombatScreen.tsx`, `src/web-ui/src/features/combat-map/CombatMapView.tsx` | 이동·일반 행동·주문 선택→가리킴 preview→실행; 단일 선택이면 클릭, 다중 대상/배분이면 명시적 시전; 관련된 경우에만 고도 입력 |
표의 경로는 기존 파일인지 신규 제안인지 각 행에 표시했다. 신규 SpellAction 타입만 proposed file이며 현행 파일이라는 뜻이 아니다. 기존 MCP 연동은 읽기 전용 검색 범위로 유지하며 새 MCP 서버를 추가하지 않는다.
## 5.3 Data, Communication, and Persistence

- 동기 HTTP/기존 포트: adventure → map/character/dice; adventure → AI GM 계획; scenario preparation → document knowledge 참조. 새 서비스·브로커·MCP 없음.
- 비동기 메시지 계약: 신규 메시지를 만들지 않는다. 기존 명령/operation 저장과 응답 계약을 재사용한다.
- 원본 정의와 Source Span은 Document Knowledge가 불변으로 소유한다. `ScenarioPackage` JSON에는 기본 룰북 전체 정의 인벤토리, 선택 추가 자료 정의, 모험별 사용자 수정/승인, 원문·추출 버전 참조를 추가한다. Bundle Lock은 이 정확한 버전을 고정한다.
- 전투 operation 저장은 기존 commandId/operation persistence를 확장해 단계와 확정 결과를 보존한다. CombatEncounter와 지도 등 서비스별 저장소의 소유권은 유지한다.
- 스키마 변경은 additive JSON/operation 필드로 진행한다. 기존 데이터에 지원 주문·특성·값을 조용히 기본값으로 채우지 않는다. 준비 중 package는 사용자 검토가 필요하다. 이미 시작된 모험은 잠긴 정의 버전을 유지하며 명시적 마이그레이션 전까지 호환 처리한다.
# 6. Runtime Design

- Preview는 공개 projection과 정의/상태 버전을 읽고 계산만 한다. 실행은 `commandId`, 기대 encounter/map/definition 버전을 검사하고 기존 operation 경로에서 상태·비용을 반영한다.
- 시전·턴 자원 예약 및 소유자는 `CombatEncounter`/기존 runtime 명령이다. 각 서비스가 자기 데이터만 쓴다. 분산 ACID를 주장하지 않는다.
- operation/commandId 중복은 기존 operation 결과를 반환한다. 결과가 불명확하면 먼저 같은 ID로 조회·조정하고 맹목 재호출하지 않는다. 알 수 없는 작업 이름은 실행 전에 거부한다. 호출 결과가 불명확하면 재계획 전에 기존 operation을 조회해 이미 반영되었는지 확인한다.
- 기본 효과와 비용이 확정된 뒤 추가 환경 응용은 별도 단계다. 추가 응용 실패/보정은 기본 결과를 보상 취소하지 않으며, 재시도도 기본 효과/비용을 중복 적용하지 않는다.
- 이동은 캐릭터 속도와 현재 적용 중인 속도 변경 효과를 기준으로 계산하고, 매 턴 `TurnResources`에 기록된 잔여 이동량을 확인한다. 이동은 행동이나 공격 전·사이·후에 나누어 할 수 있으며 매 전투 턴 명시적으로 종료한다. 중단 시 확정된 실제 칸과 그 시점까지 비용만 반영하고 미도달 경로 비용은 쓰지 않는다.
- 화면 좌표는 기존 공용 screen-to-grid 변환을 사용한다. 줌 상태에서도 토큰/overlay/포인터는 같은 변환을 따른다. 전장의 안개는 공개 경계 필터와 안개 경계의 clipping/mask를 함께 적용해 벽·공간 형상이 어두운 미탐험 셀 위로 그려지지 않게 한다. 확정 이벤트/응답은 적 비공개 정보가 제거된 Player projection을 사용한다.
# 7. Error Handling and Recovery
| 오류 | 분류/처리 |
|---|---|
| 이전 encounter/map/정의 버전 | 충돌; 비용 없이 거부하고 최신 공개 상태 반환 |
| 미지원 trait/정의/무효 선택 | 비재시도 규칙 거부; 구조화 오류. 기본 룰북 주문 전체 지원 인벤토리에서는 어떤 주문 결과도 미구현으로 남기지 않음 |
| GM 작업 없음/권한 또는 스키마 실패 | 호출 전에 거부; GM에 허용 목록/오류 반환, 환경 응용만 수정 |
| 환경 작업 호출 오류 | 실패한 호출, 구조화된 오류, 현재 지원 작업 목록·제약을 GM에 반환해 입력/작업 수정·재시도. 수치 횟수 한도나 자동 영구 반복을 추가하지 않음 |
| 외부 명령 결과 불명 | commandId로 상태 조회 후 미반영이면 같은 operation 재개; 반영 확인 전 새 명령 금지 |
| 기본 주문 실행 전 실패 | 기존 operation의 예약/보상 정책에 따르며 기본 효과·비용 미확정 |
| 기본 효과 확정 후 환경 응용 실패 | 추가 응용만 실패; 기본 효과·비용 유지, 비가시적 내부 실패·함정 상태는 숨김 |
| 이동 중 규칙상 중단 | 실제 도달 단계·비용 유지; 시스템 오류는 기존 이동 예약/operation 복구 정책 적용 |
성공/완료 메시지는 모든 필수 소유자 상태가 확인된 뒤 반환한다. 부분 장애는 기존 Runtime Command Saga/operation 경계에서 조회·재개하며 별도 saga 서비스나 cross-context 트랜잭션은 만들지 않는다.
# 8. Security

- 사용자 API는 기존 authenticated owner 검사와 패키지/모험 소유권 검사를 유지한다. 수정·승인 요청은 owner와 expected revision을 확인한다.
- 사용자가 제출한 definition/selection은 enum·범위·대상·버전·필수 필드 및 중첩 입력 구조를 검증한다. 클라이언트 파생 AC/피해/사거리 값은 권위 입력으로 신뢰하지 않는다.
- GM 작업은 capability scope, tool registry, 입력 스키마, 대상·owner·버전을 실행 전과 실행 경계에서 재검사한다. AI가 임의 코드/함수 이름/비등록 작업을 실행할 수 없다.
- AI는 숨겨진 지도/적/함정 상태의 원본을 받지 않고 허용된 공개 projection 및 해당 작업에 필요한 비밀 범위만 받는다. Player 응답·미리보기는 공개 필터 뒤에만 제공한다.
- 내부 도구 오류는 보정에 필요한 범위만 GM에 제공하고, 숨겨진 사실·내부 판정·비밀 자격증명을 플레이어에게 노출하지 않는다. 로그에는 원문·비밀 지도 데이터·인증 토큰을 남기지 않는다.
# 9. Observability

- 구조화 로그: `adventureId`, `encounterId`, `commandId`, 정의/패키지/지도 버전, trait 식별자, operation 상태, 오류 코드. 사용자 원문과 비공개 사실은 기본 기록하지 않는다.
- 지표: 미리보기/실행/버전 충돌/미지원 거부 수, 단계별 operation 지연·복구·불명 결과, trait별 성공·거부, tool preflight/call/repair 결과, 준비 중 검토 대기/수정/승인 수.
- trace: API → Adventure Runtime → trait evaluation → 기존 Map/Character/Dice/GM gateway 경계를 연결한다. commandId와 각 원격 operation ID를 상관관계 키로 사용한다.
- 알림 기준은 운영 측정 뒤 정한다. 응답 지연 수치 SLA, 재시도 최대 횟수, 동시성 수치를 새로 만들지 않는다.
# 10. Change Boundaries

## 10.1 허용

기존 ScenarioPackage/Bundle, Adventure Runtime combat domain/application/API, GM planning/tool gateway, Combat Map API/domain, 기존 UI를 확장한다. 별도 service/module/database, MCP 서버 또는 arbitrary code executor를 만들지 않는다.
## 10.2 금지

공개 룰북 원본 수정, 사용자 수정본을 다른 모험에 전파, 정의 의미 전체를 자동 검증했다고 가장하기, 미지원 기본 주문을 후보표시만으로 지원 처리하기, 클라이언트 권위 판정, 지도/Character/Dice 정본 복제, AI 임의 코드 실행, 환경 응용 실패로 기본 주문 효과/비용 롤백, 새로운 독립 saga 서비스 추가.
## 10.3 조건부

기존 시작 모험의 저장 형식 변경은 명시적 데이터 변환·호환 계획과 검증이 있어야 한다. 새 trait 추가는 실제 Basic Rules/선택 자료 규칙의 실행 필요성이 확인될 때 코드와 inventory acceptance를 함께 확장한다. 외부 AI 전송 필드 확장은 기존 internal HTTP 계약과 provider/local planning 양쪽이 입력·오류를 보존할 때만 허용한다.
# 11. Verification Requirements

아래는 구현 후 요구할 검증 계약이며, 이 문서 작성 시 실행 결과를 주장하지 않는다.
| 범주 | 검증 |
|---|---|
| Domain | 공식 2014 Basic Rules 전체 spell inventory와 고유 ID/source 연결, 모든 주문의 실제 엔진 실행 결과; 비전투/의식/긴 시전/소환·조종/지속 발동/단계 상승; 추가 자료의 비활성/필수 차단 |
| Program | `SpellTrait` 다중 구현과 결정적 조합, preview 순수성, source/evidence 보존, only-supported trait 결과, API DTO/오류, 의존성 경계 |
| Technical | 기존 controller 계약·중첩 schema·expected versions·idempotency; JSON additive migration; public map projection |
| Runtime | Action 전/사이/후 이동, 각 벽·문·모서리·지형·점유/상태 경로 비용, 실제 중단 비용, 고도·엄폐·시야·확산 별도 판정, 최신 버전 거부 무비용 |
| Recovery | 기본 효과 확정 뒤 환경 도구 오류/보정/재시도에서 spell cost/base effect 중복 없음; timeout unknown은 commandId 조회; 지원 밖 작업은 호출 전 거부 |
| Security/UI | 비공개 적/벽/함정 데이터가 preview, 확대, 오류, 로그를 통해 누출되지 않음; 단일 대상 클릭과 다중 대상 명시 시전, 확대 중 포인터/오버레이 정렬 |
| Preparation | 원문·추출 버전·사용자 수정 diff/provenance, 사용자 승인 전 모험 시작 차단, 승인본만 현재 모험에 잠금, 다른 모험/게시 원본 불변 |
## 13.3 Architecture Topic Coverage

| Topic | Status | Basis |
|---|---|---|
| 설계 범위와 제품 요구사항 연결 | SETTLED | Product UC/BR/AC 및 사용자가 확정한 범위 |
| 도메인 흐름과 주요 위험 지점 | SETTLED | 사용자가 확정한 준비·시전·이동·환경 응용 흐름 |
| 도메인 경계와 새 경계 도입 여부 | SETTLED | 기존 경계만 사용하며 새 경계·모듈·서비스를 만들지 않음 |
| 경계 간 관계와 규칙 소유권 | SETTLED | `CONTEXT-MAP.md` 및 전투·지도·캐릭터·주사위 소유권 결정 |
| 엔티티·값 자료형·서비스 | SETTLED | 확정된 정의·선택·미리보기 자료형과 특성별 실행 전략 |
| 상태와 저장소 경계 | SETTLED | 기존 ScenarioPackage·Bundle 및 실행 명령 소유권; ADR-003 |
| 프로그램 설계 | SETTLED | 사용자 답변과 Product AC/BR의 입력·작업 호출·오류 보정 계약; ADR-018/019의 권한 경계 |
| 기술 구조 | SETTLED | 기존 서비스·API·파일 경계 사용; 새 통신 서비스 없음 |
| 실행 시 동작 | SETTLED | 사용자 답변과 ADR-018/019: 버전·중복 방지·효과 순서·실제 이동 비용 |
| 오류 처리와 복구 | SETTLED | 사용자 확정 보정·재시도; 횟수 상한 없음; 불명 결과는 명령 ID로 조회 |
| 보안과 운영 관측 | SETTLED | Product 숨김 정보 규칙 및 ADR-018/019의 소유권·권한 범위 검사 |
| 변경 범위와 검증 | SETTLED | Product 범위와 구현 후 검증 계약 |
| 대안·위험·미결 질문 | SETTLED | 사용자의 완료 확인 답변은 추가 변경 없음; ST-005 응답 수치만 미정이며 설계 차단 사유는 아님 |

User completion answers settle target choices. Current-code observations above are labeled as current gaps; they are not treated as approval of the target design. No tests were run for this documentation task.

# 12. Alternatives and Trade-offs
| 선택 | 대안 | 채택 이유/비용 |
|---|---|---|
| 기존 서비스 경계 안에서 구현 | 새 Spell service/context | combat state·operation·Map/Character/Dice와 동기 조정이 필요하고 독립 lifecycle 근거가 없음; 새 경계는 호출/버전/장애 조정 비용을 추가 |
| 규칙 데이터 + 복수 trait 전략 | 주문마다 별도 하드코딩 또는 전부 데이터만으로 해석 | 수치·조합을 데이터에 두고 서로 다른 규칙 기제는 코드 전략으로 보존; 코드 없는 만능 스키마와 주문별 복제 모두 피함 |
| 사용자의 의미 검토 + 제한된 자동 검사 | 완전 자동 의미/충돌 판정 | Product 결정에 맞음; 모든 semantics 자동 검증을 주장하지 않음. 대신 승인 전 화면과 출처/수정 provenance 필요 |
| 기존 GM 도구 게이트웨이 강화 | 새 MCP 서버/직접 AI mutation | 현재 allowlist·권한·operation 경계를 재사용하고, transport 선택을 선결하지 않음 |
| 기본 효과 확정 후 추가 응용 | 전체 주문을 하나의 불투명 GM 호출로 합침 | 오류·재시도가 기본 비용/효과를 중복하거나 되돌릴 수 없도록 단계 경계 분리 |
# 13. Risks and Open Questions

## 13.1 Risks
| 위험 | 영향 | 완화 |
|---|---|---|
| 기본 주문 inventory/기제가 누락 | “전체 지원”이 거짓이 되고 전투 규칙 불일치 | 권위 source inventory 기준 고유 식별자와 실제 실행 acceptance 전수화 |
| 공개 projection과 권위 문맥 혼합 | 숨겨진 벽·함정·적 정보 누출 | preview/GM/player read model 분리 및 공개 경계 검증 |
| 환경 도구 호출 결과가 모호함 | 재시도 중 부작용 중복 | 기존 commandId operation query/reconcile 후에만 이어 실행 |
| 사용자 의미 수정본 source가 유실 | 검토 근거와 재현성 상실 | 원문 불변 참조, 모험별 diff/provenance, bundle/start lock |
| 레거시 spellName 사용 | 여러 정의 중 잘못된 버전 선택 | 유일한 잠긴 정의로만 변환, 그 외 거부 후 사용자 검토 |
## 13.2 Open Questions

없음. 사용자 편집본 범위, 실행 권위, 재시도, 도구 목록/오류 흐름은 확정되었다. ST-005의 수치 응답 목표는 미정이지만 Architecture 선택의 차단 질문이 아니며 이 문서에서 SLA를 추론하지 않는다.
