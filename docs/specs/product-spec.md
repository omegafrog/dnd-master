# Product Spec: 근거 기반 전투 계획과 실패 폐쇄형 GM 응답

## 1. Problem and Context

공개된 STORYBOOK과 RULEBOOK 근거가 모험 계획과 GM 턴까지 전달되더라도, 현재 제품은 계획이 실제 전투를 운영할 수 있는 구조를 갖추었는지와 GM 응답이 플레이어 행동에 의미 있게 답했는지를 충분히 보증하지 못한다.

계획 본문에 적이나 보스가 등장해도 구조화된 적 목록, 전투 시작 조건, 성공 결과, 실패 후 진행, 보상이 비어 있을 수 있다. 이 상태에서도 계획은 READY가 될 수 있어 전투 단계가 실제 실행 직전에야 불완전하다는 사실이 드러난다. 반대로 미래 단계의 구체적인 좌표와 배치를 계획 생성 시점에 확정하면 플레이 중 선택으로 바뀔 수 있는 상황을 너무 일찍 고정한다.

GM 턴에서는 공급자가 서술, 판정 또는 인용을 누락해도 중립 문장과 검색 상위 근거가 자동으로 보충되어 정상 턴처럼 확정될 수 있다. 또한 사용자가 선택한 공급자·모델과 실제 호출된 공급자·모델이 다르더라도 표시 정보만으로 이를 구분하기 어렵다. 그 결과 RAG 연결성은 정상이어도 플레이 품질 실패가 가려지고, 어떤 모델의 품질을 평가했는지 신뢰할 수 없다.

## 2. Goals and Desired Outcomes

- G-COMBAT-001: 전투가 포함된 모험 단계는 실제 진행에 필요한 근거 기반 전투 골격을 갖춘 경우에만 준비 완료가 된다.
- G-COMBAT-002: 전투 골격은 출처가 있는 설정 사실과 GM이 작성한 실행용 진행 조건을 구분한다.
- G-COMBAT-003: 미래 전술 장면의 세부 배치는 해당 단계가 현재 단계가 될 때까지 지연하되, 준비 필요 여부는 계획에서 명확히 알 수 있다.
- G-REPAIR-001: 계획 검증 차단은 기존 계획 전체와 함께 회귀 보정되며, 관련 필드의 일관성을 회복한 뒤 전체 계획을 다시 검증한다.
- G-GM-001: 플레이어 행동에 대한 의미 있는 서술, 판정, 관련 근거가 모두 유효한 GM 응답만 턴으로 확정된다.
- G-GM-002: 공급자 응답 실패는 중립 문장이나 임의 인용으로 숨기지 않고 재시도 가능한 실패로 노출된다.
- G-PROVIDER-001: 요청한 공급자·모델과 실제 사용된 공급자·모델을 구분하고, 실제 실행 정보를 품질 평가와 운영 기록의 기준으로 삼는다.
- G-QUALITY-001: 깨끗한 데이터에서 모험 계획 생성부터 5개 플레이어 질문까지 반복 가능한 품질 여정으로 회귀를 검증한다.

## 3. Users and Actors

- **Solo Player**: 검증된 자료로 모험을 준비하고, AI Game Master와 실제 모험을 진행하는 사용자다.
- **Adventure Planning**: STORYBOOK과 RULEBOOK 근거를 바탕으로 모험 단계, 전투 골격, 분기와 결말을 준비하고 검증하는 제품 역할이다.
- **AI Game Master**: 현재 모험 상태와 제한된 근거를 사용해 장면 서술과 행동 판정 후보를 생성한다.
- **Adventure Runtime**: 공급자 후보를 검증하고 성공한 GM Turn만 원자적으로 확정하는 제품 역할이다.
- **Document Knowledge**: 공개된 Extraction Version에서 세션 범위의 STORYBOOK과 RULEBOOK 근거를 검색해 제공한다.
- **GM Provider**: 구조화된 계획 후보 또는 GM 응답 후보를 생성하는 외부·로컬 AI 공급자다.

## 4. Ubiquitous Language and Terminology

- **Combat Skeleton**: 전투가 있는 단계의 목적, 참가자, 시작 조건, 성공 결과, 실패 또는 fail-forward 결과, 보상을 근거와 함께 표현한 실행 전 계획이다. 좌표·시야·토큰 배치와 같은 현재 전술 장면의 세부 상태는 포함하지 않는다.
- **Combat Requirement**: 단계가 전투를 포함하지 않는지, 선택적으로 포함할 수 있는지, 반드시 포함하는지를 나타내는 분류다.
- **Source Fact**: 적·보스·장소·수량·아이템·보상처럼 STORYBOOK 또는 RULEBOOK 근거가 직접 지지해야 하는 사실이다.
- **GM Scaffolding**: 다음 단계로 진행하는 조건, 실패 후 이야기의 계속 방식처럼 플레이를 운영하기 위해 작성하는 구조다. 출처 문장을 그대로 복제할 필요는 없지만 근거에 없는 설정 사실을 추가할 수 없다.
- **Fail-forward Outcome**: 실패가 모험 전체를 정지시키지 않으면서 비용, 위험, 정보 손실 또는 다른 경로로 진행을 계속시키는 결과다.
- **Tactical Preparation Requirement**: 전술 장면이 불필요한지, 미래 단계라 준비 대기 중인지, 현재 준비 중인지, 준비되었는지, 준비에 실패했는지를 구분하는 상태다.
- **Projection Blocker**: 구조화된 모험 계획이 실행 가능성, 근거성 또는 필드 간 일관성을 충족하지 못했음을 나타내는 검증 결과다.
- **Dependency-aware Repair**: Projection Blocker가 가리킨 필드뿐 아니라 같은 원인에 의존하는 관련 필드를 함께 회귀 수정하되, 무관한 단계와 사실은 보존하는 보정이다.
- **Requested Provider Selection**: 세션 또는 요청에서 사용자가 선택한 GM 공급자, 모델과 추론 설정이다.
- **Effective Provider Selection**: 해당 요청을 실제로 처리한 공급자, 모델, 엔드포인트와 설정 버전이다.
- **Meaningful GM Response**: 최신 플레이어 행동을 반영하고, 현재 장면과 모순되지 않으며, 비어 있지 않은 서술과 판정을 제공하고, 주장에 관련된 근거만 인용하는 응답이다.
- **Retryable GM Turn Failure**: GM 후보가 생성·구조·근거·안전·관련성 검증을 통과하지 못해 모험 상태와 대화가 바뀌지 않은 실패다.

## 5. Core Use Cases

### UC-COMBAT-001: 근거 기반 전투 골격이 있는 모험 계획 생성

1. Solo Player가 공개된 STORYBOOK과 RULEBOOK을 포함하는 세션에서 모험 계획 생성을 요청한다.
2. Adventure Planning은 각 단계가 요구하는 전투 수준을 결정한다.
3. 전투가 필요한 단계에는 Source Fact에 연결된 참가자와 전투 목적을 포함한다.
4. 각 전투 단계에는 시작 조건, 성공 결과, 실패 또는 fail-forward 결과를 포함한다.
5. 계획 전체가 근거, 구조, 분기 및 필드 간 일관성을 통과한 경우에만 READY가 된다.

### UC-COMBAT-002: 현재 단계 진입 시 전술 장면 준비

1. 계획 생성 시 미래 전투 단계는 전술 준비가 필요함을 표시하지만 좌표와 토큰 배치를 확정하지 않는다.
2. 모험 시작 또는 단계 전환으로 해당 단계가 현재 단계가 되면 전술 준비를 시작한다.
3. 준비된 전술 장면의 참가자는 Combat Skeleton의 참가자와 일치해야 한다.
4. 준비가 완료된 뒤에만 해당 단계의 전술 맵을 활성화한다.

### UC-REPAIR-001: 차단된 계획의 의존성 기반 회귀 보정

1. 계획 검증이 Projection Blocker와 영향을 받는 계획 영역을 반환한다.
2. 보정은 기존 전체 계획, 차단 원인, authoritative 근거를 입력으로 사용한다.
3. 보정은 차단된 필드와 그에 의존하는 같은 단계의 필드를 함께 수정할 수 있다.
4. 무관한 단계와 이미 검증된 사실은 그대로 유지한다.
5. 보정된 전체 계획을 처음부터 다시 검증하고, 차단이 남으면 READY로 만들지 않는다.

### UC-GM-001: 의미 있는 GM 턴 확정

1. Solo Player가 현재 장면에서 행동 또는 질문을 입력한다.
2. Adventure Runtime은 현재 단계, 최근 턴, 캐릭터 상태와 질문 의도에 관련된 공개 근거만 준비한다.
3. GM Provider가 서술, 판정, 인용을 포함한 후보 응답을 생성한다.
4. Adventure Runtime은 최신 행동 반영, 구조, 근거 범위, 인용 관련성, 숨김 정보와 상태 안전성을 검증한다.
5. 모든 검증을 통과한 경우에만 GM Turn과 모험 버전, 대화를 함께 확정한다.

### UC-GM-002: 잘못된 GM 응답의 제한 보정과 재시도

1. 최초 GM 후보가 검증에 실패하면 제품은 원본 후보와 구체적인 위반 사항을 사용해 한 번 보정한다.
2. 보정 후보도 동일한 전체 검증을 받는다.
3. 보정이 성공하면 정상 턴으로 확정한다.
4. 보정이 실패하면 모험 상태와 대화를 보존하고 Retryable GM Turn Failure를 반환한다.
5. Solo Player는 같은 행동을 안전하게 다시 시도할 수 있다.

### UC-PROVIDER-001: 실제 공급자 선택 확인

1. 세션이 Requested Provider Selection을 가진다.
2. GM 요청 처리 전에 제품이 Effective Provider Selection을 하나로 확정한다.
3. 후보 생성과 품질 기록은 Effective Provider Selection을 사용한다.
4. 요청값과 실행값이 다르면 차이를 숨기지 않고 운영 진단에서 확인할 수 있다.

### UC-QUALITY-001: 실제 모험 여정 품질 검증

1. 개발 RAG 데이터를 초기화하고 원본 RULEBOOK과 STORYBOOK을 새 발행 파이프라인으로 처리한다.
2. 공개된 근거로 시나리오와 모험 계획을 새로 생성한다.
3. 계획의 전투 골격, 근거 및 전술 준비 상태를 검사한다.
4. 모험을 시작하고 서로 다른 유형의 질문 또는 행동을 5회 진행한다.
5. 각 턴의 응답 품질, 인용 존재·정확성·관련성, 실제 공급자 정보, 상태 원자성을 검증한다.

## 6. Business Rules and Invariants

- BR-COMBAT-001: 단계 본문이나 근거가 전투, 적 또는 보스를 요구하면 Combat Requirement와 Combat Skeleton은 비어 있을 수 없다.
- BR-COMBAT-002: 필수 전투 단계에는 적 또는 보스, 전투 목적, 시작 조건, 성공 결과, 실패 또는 fail-forward 결과가 모두 존재해야 한다.
- BR-COMBAT-003: Source Fact는 해당 단계가 선택한 공개 근거가 직접 지지해야 한다.
- BR-COMBAT-004: GM Scaffolding은 근거에 없는 적·장소·보상·수치·고유명사를 새로 만들 수 없다.
- BR-COMBAT-005: 전술 장면의 적과 보스는 Combat Skeleton의 참가자와 동일한 정체성과 수량 범위를 가져야 한다.
- BR-COMBAT-006: 미래 단계의 전술 배치는 미리 확정하지 않지만, 전술 준비 필요 여부는 전술 불필요와 구분되어야 한다.
- BR-COMBAT-007: 전술 준비가 필요한 현재 단계는 준비 완료 전까지 전술 맵을 활성화할 수 없다.
- BR-REPAIR-001: 보정은 Projection Blocker와 명시된 의존 영역 밖의 계획을 변경할 수 없다.
- BR-REPAIR-002: 보정 결과는 부분 검증이 아니라 전체 계획 검증을 다시 통과해야 한다.
- BR-REPAIR-003: 동일한 차단 결과를 진전 없이 반복하면 자동 성공이나 빈 값 제거로 우회하지 않고 차단 상태를 유지한다.
- BR-GM-001: narration, judgment 또는 필요한 citation이 누락된 후보는 Meaningful GM Response가 아니다.
- BR-GM-002: 중립 서술·중립 판정·검색 상위 근거 자동 첨부는 누락된 공급자 응답을 성공으로 바꾸는 수단으로 사용할 수 없다.
- BR-GM-003: 인용은 세션 Evidence Pack 안에 존재해야 하며, 동시에 응답에서 사용된 구체적인 주장을 지지해야 한다.
- BR-GM-004: 공급자 후보가 최초와 보정 모두 실패하면 GM Turn, 모험 버전, 대화, 단계 진행은 변경되지 않는다.
- BR-GM-005: 같은 실패 요청의 재시도는 중복 턴이나 중복 상태 변경을 만들지 않는다.
- BR-PROVIDER-001: Effective Provider Selection은 호출 전에 하나로 확정되며 응답 메타데이터로 사후 위장할 수 없다.
- BR-PROVIDER-002: 품질 지표와 턴 기록은 Requested Provider Selection과 Effective Provider Selection을 모두 구분해 추적한다.
- BR-QUALITY-001: 구조·안전성 통과만으로 생성 품질을 통과 처리하지 않으며, 최신 행동 반영과 근거 관련성을 별도로 평가한다.

## 7. States and State Transitions

### Adventure Story Plan

`GENERATING → VALIDATING → READY`

- 검증 차단: `VALIDATING → BLOCKED`
- 보정 시작: `BLOCKED → REPAIRING`
- 보정 성공: `REPAIRING → VALIDATING → READY`
- 보정 실패 또는 진전 없음: `REPAIRING → BLOCKED`

### Tactical Preparation Requirement

- 전술 불필요: `NOT_REQUIRED`
- 미래 단계 준비 대기: `REQUIRED_PENDING`
- 현재 단계 준비: `REQUIRED_PENDING → PREPARING`
- 준비 성공: `PREPARING → READY`
- 준비 실패: `PREPARING → FAILED_RETRYABLE`
- 재시도: `FAILED_RETRYABLE → PREPARING`

### GM Turn

`REQUESTED → GENERATING → VALIDATING → COMMITTED`

- 최초 후보 실패: `VALIDATING → REPAIRING`
- 보정 성공: `REPAIRING → VALIDATING → COMMITTED`
- 공급자·보정·검증 실패: `GENERATING | REPAIRING | VALIDATING → FAILED_RETRYABLE`
- 재시도: `FAILED_RETRYABLE → GENERATING`
- `COMMITTED`만 모험 버전과 대화를 변경한다.

## 8. Failures, Exceptions, and Boundary Conditions

- 전투를 암시하는 본문과 빈 전투 구조가 함께 존재하면 계획을 차단한다.
- 적 정체성은 근거가 있으나 정확한 수량이 불명확하면 수량을 발명하지 않고 GM 확인 또는 허용 범위로 표시한다.
- Source Fact 근거가 부족하면 일반적인 룰북 장 제목이나 다른 단계의 근거로 대체하지 않는다.
- 전투가 없는 사회·탐험 단계에는 빈 적 목록이 허용되지만 전투 불필요 상태가 명시되어야 한다.
- 현재 단계에 필요한 전술 장면 생성이 실패하면 텍스트 진행으로 자동 우회하지 않고 재시도 가능한 준비 실패를 표시한다.
- GM Provider가 빈 객체, 잘못된 구조, 빈 narration, 빈 judgment 또는 유효하지 않은 citation을 반환하면 성공 턴으로 간주하지 않는다.
- 인용이 Evidence Pack에 존재해도 응답 주장과 관련이 없으면 검증 실패다.
- 공급자 요청값과 실제 실행값이 다르면 실제 실행값을 숨기지 않으며, 차이 원인을 진단할 수 있어야 한다.
- 공급자 실패와 검증 실패는 이미 확정된 모험 상태를 훼손하지 않는다.

## 9. Inputs and Outputs

### Inputs

- 공개된 STORYBOOK, RULEBOOK 및 Scenario Package 근거
- 단계별 목표, 갈등, 분기, 결말과 맵 연결 정보
- 파티 구성과 현재 모험 단계
- Solo Player의 최신 행동 또는 질문
- Requested Provider Selection

### Outputs

- 단계별 Combat Requirement와 Combat Skeleton을 포함한 Adventure Story Plan
- 단계별 근거와 Projection Blocker
- Tactical Preparation Requirement와 현재 준비 결과
- 검증된 narration, judgment, citation을 포함한 GM Turn
- Retryable GM Turn Failure와 재시도 가능 여부
- Requested Provider Selection과 Effective Provider Selection을 구분한 품질 기록

## 10. Scope and Non-goals

### Scope

- 모험 계획의 전투 실행 준비도와 근거 검증
- Projection Blocker 기반의 의존성 회귀 보정
- 현재 단계 진입 시점의 전술 장면 지연 준비
- GM 응답의 구조, 행동 관련성, 인용 관련성 및 실패 원자성
- 실제 공급자·모델 식별과 품질 측정
- 개발 데이터 초기화부터 5개 GM 턴까지의 반복 가능한 품질 검증

### Non-goals

- 모든 미래 전술 장면의 좌표·토큰·시야를 계획 생성 시점에 확정하는 것
- STORYBOOK에 없는 적, 보스, 보상 또는 결말을 채우기 위해 창작하는 것
- AI Game Master에게 검증되지 않은 상태 변경 권한을 부여하는 것
- Adventure Story Plan의 숨겨진 내용을 Solo Player에게 노출하는 것
- 기존 문서 전처리와 RAG 발행 계약 자체를 다시 설계하는 것
- 특정 AI 모델 하나를 제품 요구사항으로 고정하는 것

## 11. Priorities and Trade-offs

1. **실패 가시성 우선**: 무응답을 자연스러운 척 보이게 하는 것보다 재시도 가능한 실패를 정확히 노출한다.
2. **근거 정확성 우선**: 계획의 풍부함보다 Source Fact의 단계·필드 단위 추적성을 우선한다.
3. **실행 준비도 우선**: 전투가 언급된 풍부한 본문보다 실제 운영 가능한 Combat Skeleton을 우선한다.
4. **지연 확정 우선**: 미래 전술 장면의 즉시 사용 가능성보다 플레이 선택을 반영할 수 있는 지연 준비를 우선한다.
5. **실제 실행 정보 우선**: 사용자가 요청한 모델 이름보다 실제 호출된 공급자·모델을 품질 판단 기준으로 삼는다.
6. **작은 근거 집합 우선**: 많은 청크를 전달하는 것보다 현재 행동과 단계에 관련된 근거를 선별한다.

## 12. Success Conditions and Acceptance Criteria

- AC-COMBAT-001: STORYBOOK에 거대 쥐 전투가 있는 단계는 적 정체성·수량 범위·전투 목적·시작 조건·성공 결과·실패 또는 fail-forward 결과를 가진다.
- AC-COMBAT-002: STORYBOOK에 최종 보스가 있는 단계는 보스와 전투 결과가 구조화되고 각 Source Fact가 해당 단계 근거로 추적된다.
- AC-COMBAT-003: 전투를 설명하는 단계의 적·보스 필드가 비어 있으면 계획은 READY가 되지 않는다.
- AC-COMBAT-004: 모든 필수 전투 단계는 빈 failure 결과 대신 명시적인 실패 또는 fail-forward 결과를 가진다.
- AC-COMBAT-005: 미래 전투 단계는 `REQUIRED_PENDING`으로 구분되고, 현재 단계가 되기 전에는 구체적인 전술 좌표를 확정하지 않는다.
- AC-COMBAT-006: 현재 필수 전투 단계의 전술 장면은 Combat Skeleton과 참가자가 일치해야 READY가 된다.
- AC-EVIDENCE-001: 일반적인 룰북 장 설명만으로 특정 방, 적, 보스 또는 보상을 지지했다고 판정하지 않는다.
- AC-REPAIR-001: 적 누락 blocker를 보정할 때 같은 단계의 전투 요구, 결과와 전술 준비 상태를 함께 일관되게 수정할 수 있다.
- AC-REPAIR-002: 보정은 무관한 단계의 검증된 사실을 변경하지 않으며 전체 계획 검증을 다시 통과해야 한다.
- AC-GM-001: 5개의 실제 질문 또는 행동 모두 최신 입력을 반영한 자연스러운 한국어 narration과 judgment를 반환한다.
- AC-GM-002: 5개 턴 어디에도 “서술 누락”, “판정 누락”, “인용 자동 첨부” 중립 fallback 경고가 존재하지 않는다.
- AC-GM-003: 모든 인용은 공개된 청크와 정확히 일치하고, 해당 응답의 구체적인 주장을 실제로 지지한다.
- AC-GM-004: 공급자 후보와 한 번의 보정이 모두 실패하면 턴은 `FAILED_RETRYABLE`이며 모험 버전, 대화, 단계가 바뀌지 않는다.
- AC-PROVIDER-001: 각 턴에서 Requested Provider Selection과 Effective Provider Selection을 조회할 수 있고 실제 호출 로그·저장 메타데이터가 일치한다.
- AC-QUALITY-001: 개발 RAG 초기화, 새 전처리 발행, 시나리오 준비, 계획 생성, 모험 시작, 5개 턴 실행의 전체 여정이 반복 가능하다.
- AC-QUALITY-002: 품질 게이트는 구조 성공률 외에 행동 반영률, 중립 fallback 비율, 인용 정확도, 인용 관련성, 공급자 일치율을 보고한다.
