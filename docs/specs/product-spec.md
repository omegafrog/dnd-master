# Product Spec: GM Knowledge Base, Retrieval, and Game Assets

## 1. Problem and Context

Solo Player가 업로드하는 룰북과 모험 시나리오는 구조와 사용 목적이 다르다. 룰북은 판정·전투·주문처럼 재사용되는 규칙 근거이고, 모험 시나리오는 장면·비밀·조건·전환이 결합된 실행 자료다. 지도와 Player Handout은 검색용 텍스트보다 게임 중 표시·공간 참조되는 시각 자산이다. 문서를 단순 텍스트 조각으로 검색하면 규칙의 상위 맥락, 장면의 공개 범위, 지도와 본문의 관계, 모험 진행 구조가 사라진다.

AI Game Master는 현재 행동을 판단할 때 관련 규칙, 현재 장면의 공개·비밀 정보, 엔티티, 표시 가능한 자산, 확정 런타임 상태를 서로 다른 권위로 조회해야 한다. 검색 결과는 판단 근거이며, 자산 시스템은 원본과 장면 연결을 제공한다. 어느 쪽도 판정이나 상태 변경을 직접 수행해서는 안 된다.

## 2. Goals and Desired Outcomes

- **PR-001** 업로드 문서의 텍스트, 계층, 표, 이미지, 원문 위치를 추적 가능한 지식으로 보존한다.
- **PR-002** 문서 성격을 구분하고 룰북과 스토리북을 서로 다른 의미 구조로 해석한다.
- **PR-003** 룰 검색 시 세부 규칙과 이를 이해하는 데 필요한 상위 규칙 맥락을 함께 제공한다.
- **PR-004** 모험 자료를 장면, 인물, 조우, 판정, 비밀, 전환으로 조회 가능하게 만든다.
- **PR-005** 플레이어 공개 정보와 GM 전용 정보를 분리해 스포일러 누출을 막는다.
- **PR-006** 지도, Player Handout과 기타 시각 자료를 원본·의미 설명·구조 메타데이터가 결합된 Game Asset으로 보존한다.
- **PR-007** 질의 의도와 현재 세션 범위에 따라 규칙·스토리·엔티티 근거를 선택한다.
- **PR-008** 정적 원본 지식, Adventure Story Plan, Runtime State를 별도 권위로 유지한다.
- **PR-009** 문서 처리 결과의 불확실성·실패·원문 근거를 사용자가 검토하고 재처리할 수 있다.
- **PR-010** Adventure Planner가 모험 전체 구조를 요약·계획할 수 있는 상위 관점을 제공한다.
- **PR-011** 장면에서 사용할 지도 영역과 Player Handout을 직접 식별하고 안전한 표시 행동을 지원한다.

## 3. Users and Actors

- **Solo Player**: 문서를 업로드하고 처리 상태·경고를 검토하며 세션에 사용할 문서를 선택한다.
- **AI Game Master**: 현재 행동에 필요한 제한된 근거를 받아 판정과 서술을 결정한다.
- **Adventure Planner**: 모험 전체 구조와 분기를 근거로 Adventure Story Plan 후보를 만든다.
- **Document Knowledge**: 원본, 구조, 시각 자료, 출처와 검색 근거를 보존한다.
- **Game Asset System**: 지도, Handout, 삽화의 원본과 구조 메타데이터, 장면 연결, 공개 가능 표현을 보존한다.
- **Adventure Runtime**: 세션 범위, 현재 상태, 질의 의도를 제공하고 검색 근거를 GM 컨텍스트에 결합한다.

## 4. Ubiquitous Language and Terminology

- **Evidence Unit**: Source Span에서 파생된 검색 단위. 의미 유형, 계층, 공개 범위, 원문 출처를 유지한다.
- **Rule Evidence**: 규칙 개념과 상·하위 관계를 가진 룰북 근거.
- **Adventure Evidence**: 장면, 장소, 인물, 조우, 판정, 비밀 또는 전환을 표현하는 스토리북 근거.
- **Player-Visible Evidence**: 현재 공개 조건에서 Solo Player에게 노출 가능한 근거.
- **GM-Only Evidence**: 발견·발생 전 플레이어에게 노출하면 안 되는 비밀 근거.
- **Scene Graph**: 모험의 장면과 조건부 전환을 나타내는 구조.
- **Retrieval Evidence Pack**: 한 질의에 대해 범위·의도·공개 정책을 적용해 조합한 출처 포함 근거 묶음.
- **Content Role**: 추출 콘텐츠의 게임 내 사용 목적. `KNOWLEDGE`, `GAME_ASSET`, `GM_MATERIAL`로 구분한다.
- **Game Asset**: 게임 중 표시하거나 공간적으로 참조하는 원본 시각 자료와 파생 메타데이터. 지도, Player Handout, 삽화, 초상화, 퍼즐 자료를 포함한다.
- **Map Region**: 지도 안에서 장면 또는 위치와 직접 연결되는 식별 가능한 공간 영역. 좌표, 축척, 특징, 공개 상태를 가진다.
- **Player Handout**: Solo Player에게 원본 그대로 표시할 수 있는 퍼즐·편지·그림 등의 Game Asset. 해답과 GM 운용 정보는 포함하지 않는다.
- 나머지 용어는 `CONTEXT.md`를 따른다.

## 5. Core Use Cases

### UC-001 문서 일괄 등록과 비동기 처리

1. Solo Player가 하나 이상의 문서를 업로드하고 문서 유형을 확인하거나 수정한다.
2. 각 문서는 독립 처리 단위로 등록된다.
3. 텍스트, 문서 계층, 표, 이미지, 원문 위치가 추출된다.
4. 문서 유형에 맞는 Evidence Unit이 생성되고 검색 가능 상태가 된다.
5. 사용자는 문서별 상태, 경고, 실패 원인과 출처를 확인한다.

### UC-002 룰북 지식 구성

1. 룰북의 장·절·규칙·예시 관계를 식별한다.
2. 세부 규칙을 상위 개념과 연결한다.
3. 규칙 범주, 능력치, 기술, 대상 엔티티와 원문 출처를 기록한다.
4. 확실히 구조화할 수 없는 내용은 원문 근거로 남긴다.

### UC-003 모험 지식 구성

1. 모험의 전제, 퀘스트, 장소, 장면, NPC, 조우, 보상, 결말을 식별한다.
2. 장면별 공개 묘사와 GM 비밀을 분리한다.
3. 명시된 판정, 난이도, 성공·실패 결과를 원문 근거와 연결한다.
4. 장면 사이의 명시적 또는 근거 있는 조건부 전환을 Scene Graph로 만든다.

### UC-004 Game Asset 분류와 구성

1. 문서의 지도·삽화·도표를 감지한다.
2. 시각 자료를 지도, Player Handout, 삽화, 초상화, 퍼즐 등 사용 역할로 분류한다.
3. 원본, 의미 설명, 번호·라벨, 구조 메타데이터와 원본 위치를 함께 저장한다.
4. 관련 장면과 GM Material을 연결한다.
5. 해석이 불확실하거나 스포일러 분리가 불가능하면 경고한다.

### UC-005 지도 영역과 장면 연결

1. 지도의 격자, 축척, 번호 영역, 통로, 특징을 식별한다.
2. 각 Map Region에 안정적인 식별자와 좌표 영역을 부여한다.
3. 모험 본문의 장면·장소 번호와 Map Region을 직접 연결한다.
4. 현재 장면에서 검색 없이 연결된 지도와 영역을 조회한다.

### UC-006 Player Handout과 GM Material 분리

1. Handout 원본과 플레이어가 읽을 수 있는 내용을 Player Handout으로 보존한다.
2. 퍼즐 해답, 사용 조건, 실패 결과, 관련 장면은 GM Material로 분리한다.
3. 두 자료는 관계로 연결하되 플레이어용 반환 계약은 GM Material을 직렬화하지 않는다.
4. 장면 조건이 충족되면 AI Game Master가 Player Handout 표시 행동을 제안할 수 있다.

### UC-007 GM Turn 근거와 자산 조회

1. Adventure Runtime이 플레이어 행동, 현재 장면, Session Knowledge Set과 질의 의도를 제공한다.
2. 관련 규칙, 모험, 엔티티 근거를 각각 찾는다.
3. 세부 규칙에는 필요한 상위 규칙을 확장한다.
4. 현재 장면과 공개 정책에 맞는 Adventure Evidence를 선택한다.
5. 장면에 직접 연결된 Map Region과 표시 가능한 Game Asset을 별도 참조로 조회한다.
6. Runtime State와 섞지 않은 Retrieval Evidence Pack 및 Asset Reference를 반환한다.

### UC-008 Adventure Planner 근거 검색

1. Planner가 특정 시나리오의 전체 계획 근거를 요청한다.
2. 모험 요약, 전제, 목표, 장소, NPC, Scene Graph, 조우, 비밀, 결말, 보상을 제공한다.
3. 필요한 규칙 근거는 별도로 연결한다.
4. Planner는 이 근거로 Adventure Story Plan 후보를 만든다.

### UC-009 실패 문서 재처리

1. 사용자가 실패 또는 경고 문서를 확인한다.
2. 실패 문서 하나만 재처리한다.
3. 성공한 다른 문서와 게시된 버전은 영향을 받지 않는다.
4. 새 처리 결과는 출처와 처리 버전을 보존한다.

## 6. Business Rules and Invariants

- **BR-001** Source Span은 모든 파생 지식의 추적 가능한 정본이다.
- **BR-002** Document Type은 최소 `RULEBOOK`, `STORYBOOK`을 지원하며 등록부터 근거 반환까지 유지한다.
- **BR-003** Rule Evidence와 Adventure Evidence는 같은 검색 저장소를 쓰더라도 의미 유형과 조회 정책을 분리한다.
- **BR-004** 세부 Rule Evidence 반환 시 판정에 필요한 상위 규칙 맥락을 포함한다.
- **BR-005** 원문에 없는 판정, 난이도, 결과, 장면 전환을 확정 사실로 생성하지 않는다.
- **BR-006** 구조화할 수 없는 내용은 폐기하지 않고 원문 조회 가능한 Evidence Unit으로 강등한다.
- **BR-007** Player-Visible Evidence와 GM-Only Evidence는 명시적으로 구분한다.
- **BR-008** 플레이어용 출력에는 현재 공개 조건을 만족하지 않은 GM-Only Evidence의 내용·정체·좌표를 포함하지 않는다.
- **BR-009** 이미지 설명은 파생 해석이며 원본 이미지와 위치 참조를 대체하지 않는다.
- **BR-010** Scene Graph 전환은 원문 근거나 명시된 추론 신뢰도를 가진다.
- **BR-011** 검색 범위는 Session Knowledge Set에 포함된 정확한 문서·처리 버전으로 제한한다.
- **BR-012** 질의 의도는 `RULE`, `STORY`, `MIXED`, `UNKNOWN`이며 우선순위만 바꾸고 범위 제한을 우회하지 않는다.
- **BR-013** Retrieval Evidence Pack은 근거를 제공할 뿐 판정, 주사위 실행, 계획 확정, Runtime State 변경을 수행하지 않는다.
- **BR-014** Knowledge, Adventure Story Plan, Runtime State는 서로 별도 권위와 수명주기를 가진다.
- **BR-015** 모든 반환 근거는 문서, 처리 버전, 페이지 또는 구조 위치까지 추적 가능해야 한다.
- **BR-016** Batch Upload의 상태·실패·재시도는 문서별로 독립적이다.
- **BR-017** 게시된 처리 결과는 불변이며 재처리는 새 버전을 만든다.
- **BR-018** Document Type과 Content Role은 별도 분류다. 하나의 STORYBOOK은 Knowledge, Game Asset, GM Material을 함께 포함할 수 있다.
- **BR-019** 지도는 원본 이미지, 의미 설명, 공간 메타데이터를 함께 가지며 어느 하나가 나머지를 대체하지 않는다.
- **BR-020** Map Region은 장면 ID와 직접 연결한다. 벡터 검색은 발견 보조 수단이며 런타임 위치 조회의 정본이 아니다.
- **BR-021** Player Handout 원본과 GM 전용 해답·트리거·운용 정보는 별도 공개 경계를 가진다.
- **BR-022** 플레이어용 Asset Reference는 GM Material의 내용, 정체, 숨은 좌표를 직간접적으로 노출하지 않는다.
- **BR-023** AI Game Master는 허용된 Asset Reference만 `show_asset` 또는 `show_map` 행동으로 요청하며 임의 저장 위치를 직접 참조하지 않는다.
- **BR-024** Asset 표시 행동은 자산 공개 정책과 현재 장면 연결을 검증한 뒤에만 실행된다.

## 7. States and State Transitions

### Knowledge Document Processing

`QUEUED` → `PROCESSING` → `INDEXED`

- 처리 실패: `PROCESSING` → `FAILED`
- 재시도: `FAILED` → `QUEUED`
- 재처리 성공은 기존 게시 버전을 변경하지 않고 새 Extraction Version을 게시한다.

### Extraction Version

`DRAFT` → `VALIDATING` → `PUBLISHED`

- 검증 실패: `VALIDATING` → `REJECTED`
- `PUBLISHED`는 불변이다.

## 8. Failures, Exceptions, and Boundary Conditions

- **FR-001** 일부 문서 처리 실패가 같은 배치의 성공 문서를 막지 않는다.
- **FR-002** 텍스트 추출 실패, 암호화 파일, 손상 파일은 원인을 문서별로 표시한다.
- **FR-003** 문서 유형을 신뢰성 있게 판별할 수 없으면 사용자 확인 전 게시하지 않는다.
- **FR-004** 이미지 해석 실패가 텍스트 근거 게시를 막지는 않지만 누락 경고를 남긴다.
- **FR-005** 표·다단 레이아웃의 읽기 순서가 불확실하면 신뢰도와 원본 위치를 보존한다.
- **FR-006** 상충하는 근거는 하나로 합성하지 않고 각각의 출처와 충돌을 반환한다.
- **FR-007** 검색 결과가 부족하면 근거 없음을 명시하고 규칙이나 시나리오 사실을 생성하지 않는다.
- **FR-008** 상위 규칙 확장으로 컨텍스트 한도를 넘으면 직접 부모와 핵심 공통 규칙을 우선한다.
- **FR-009** Scene Graph가 불완전해도 원문 장면 검색은 가능해야 한다.
- **FR-010** 시각 자료에서 GM 비밀과 플레이어 공개 레이어를 안전하게 구분할 수 없으면 플레이어용 근거에 포함하지 않는다.
- **FR-011** 지도 축척이나 영역 좌표를 신뢰성 있게 추출하지 못하면 추측값을 확정하지 않고 미확정 메타데이터로 남긴다.
- **FR-012** 장면과 자산 연결이 없거나 모호하면 자동 표시하지 않고 GM 텍스트 진행은 유지한다.
- **FR-013** Handout과 해답을 안전하게 분리할 수 없으면 원본을 자동 공개하지 않는다.

## 9. Inputs and Outputs

### Inputs

- PDF 및 지원 문서 파일
- 사용자 확인 Document Type
- Session Knowledge Set과 고정 처리 버전
- 질의, 질의 의도, 현재 장면 식별자, 공개된 사실 식별자
- Adventure Planner의 전체 구조 요청

### Outputs

- 처리 상태, 경고, 실패 원인, 재시도 결과
- Source Span과 시각 자료 참조
- 계층형 Rule Evidence
- 공개 범위가 분리된 Adventure Evidence
- Scene Graph와 상위 모험 요약
- 출처·신뢰도·계층을 포함한 Retrieval Evidence Pack
- 원본·설명·공간 메타데이터를 가진 Game Asset
- 장면과 Map Region 또는 Player Handout의 직접 연결
- 플레이어 안전 Asset Reference와 표시 가능 조건

## 10. Scope and Non-goals

### In Scope

- RULEBOOK/STORYBOOK 문서 등록·구조 추출·분류·버전 관리
- 문서 계층, 표, 지도·삽화 설명과 본문 연결
- 규칙 계층과 상위 맥락 확장
- 장면·NPC·조우·판정·비밀·전환 구조화
- 지도 영역·축척·특징 추출과 장면 직접 연결
- Player Handout과 GM Material 분리
- 장면 기반 `show_asset`·`show_map` 행동에 필요한 안전한 자산 참조
- 세션 범위 및 질의 의도 기반 하이브리드 검색
- GM Turn 및 Adventure Planner용 근거 제공

### Non-goals

- 검색 시스템 자체의 게임 판정, 주사위 실행 또는 상태 변경
- Adventure Story Plan의 최종 승인과 런타임 운영
- Runtime State 저장
- 자산 표시 행동의 최종 승인 또는 UI 렌더링 구현
- 원문에 없는 규칙·장면·결말 생성
- 모든 이미지의 완전 자동 플레이어용 지도 변환
- 초기 범위에서 `SETTING`, `BESTIARY`, `SUPPLEMENT`를 독립 Document Type으로 운영

## 11. Priorities and Trade-offs

1. 원문 추적성과 비밀 보호
2. 규칙·모험 의미 정확성
3. 지식·계획·상태 경계
4. 불완전 문서의 안전한 강등
5. 검색 품질
6. 처리 속도와 저장 비용

정확한 구조화가 불가능하면 추측보다 원문 근거 보존을 우선한다. 검색 편의보다 세션 범위와 공개 정책을 우선한다.

## 12. Success Conditions and Acceptance Criteria

- **AC-001 / PR-001** 샘플 PDF 처리 후 제목 계층, 표, 이미지, 페이지·좌표가 Source Span으로 추적된다.
- **AC-002 / PR-002** 룰북과 스토리북이 서로 다른 Evidence 구조와 조회 정책으로 게시된다.
- **AC-003 / PR-003** Perception 세부 규칙 검색 시 직접 근거와 Wisdom/Ability Check 상위 맥락이 함께 반환된다.
- **AC-004 / PR-004** 샘플 모험의 장소, 공개 묘사, 숨은 적, DC 판정, 전환이 별도 근거로 연결된다.
- **AC-005 / PR-005** 미발견 비밀은 플레이어용 응답이나 근거 메타데이터에 노출되지 않는다.
- **AC-006 / PR-006, PR-011** 번호가 있는 지도 영역과 동일 번호의 본문 장면이 안정적인 ID로 양방향 추적된다.
- **AC-007 / PR-007** 동일 질의라도 `RULE`과 `STORY` 의도에 따라 우선순위가 달라지며 세션 밖 문서는 반환되지 않는다.
- **AC-008 / PR-008** 검색 인덱스 변경이 Adventure Story Plan 또는 Runtime State를 변경하지 않는다.
- **AC-009 / PR-009** 한 문서의 실패·재시도가 같은 배치의 성공 문서와 기존 게시 버전에 영향을 주지 않는다.
- **AC-010 / PR-010** Planner 요청에 전체 모험 구조, Scene Graph, 비밀, 결말을 포함한 상위 근거가 제공된다.
- **AC-011 / PR-001** 모든 검색 결과가 원본 문서와 처리 버전의 정확한 위치까지 역추적된다.
- **AC-012 / PR-005** 공개 범위를 안전하게 판별할 수 없는 이미지 근거는 플레이어용 결과에서 제외된다.
- **AC-013 / PR-006** 샘플 지도는 원본, 5 ft 격자 축척, 영역 좌표, 특징 설명을 함께 보존한다.
- **AC-014 / PR-011** 현재 장면 ID로 벡터 검색 없이 연결된 Map Region을 조회할 수 있다.
- **AC-015 / PR-005, PR-011** Player Handout을 표시할 때 퍼즐 해답·트리거·실패 결과가 응답과 자산 메타데이터에 노출되지 않는다.
- **AC-016 / PR-011** 허용되지 않았거나 장면에 연결되지 않은 `show_asset`·`show_map` 요청은 거부된다.
