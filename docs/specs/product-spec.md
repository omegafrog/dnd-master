# Product Spec: 근거 기반 캐릭터 생성 정보 추출

## 1. Problem and Context

현재 인덱싱된 RULEBOOK과 STORYBOOK에서 캐릭터 생성 정보를 가져올 때, 검색 결과를 전역 개수로 자르면서 한 문서 유형이 다른 문서 유형의 결과를 밀어낸다. 또한 룰북은 원문을 고정 길이 excerpt로 잘라 전달하므로, 실제 종족·클래스·배경·능력치 선택지가 있어도 캐릭터 블루프린트에 반영되지 않는다.

룰북은 캐릭터 생성 정보 검색의 근거이므로 RAG 검색이 가능하도록 인덱싱되어야 한다. 캐릭터 생성 정보 검색은 시나리오 컴파일 시 선택된 문서 묶음을 대상으로 수행한다.

## 2. Goals and Desired Outcomes

- G-1. 컴파일 시 선택된 RULEBOOK, STORYBOOK, HANDOUT에서 캐릭터 생성 정보를 근거 기반으로 검색한다.
- G-2. 문서 유형별 검색 결과를 독립적으로 수집하여 한 유형의 결과가 다른 유형을 배제하지 않게 한다.
- G-3. 캐릭터 필드와 태그를 `MANUAL_INPUT`, `SELECT_OPTION`, `FIXED_VALUE`로 구분한다.
- G-4. 근거가 부족한 필드·태그만 수동 입력 상태로 남기고, 나머지 추출 결과는 계속 사용할 수 있게 한다.
- G-5. 사용자가 필드 값과 태그, 선택지를 추가·수정할 수 있게 한다.
- G-6. 개발 단계에서는 출처 문서, 원문 인용, 유사도·신뢰도, 추출 상태를 확인할 수 있게 한다.

## 3. Users and Actors

- A-1. 플레이어: 캐릭터 생성 필드와 선택지를 확인·입력·수정한다.
- A-2. 시나리오 컴파일러: 선택된 문서 묶음에서 캐릭터 생성 정보를 요청한다.
- A-3. AI 추출기: 검색된 근거에서 필드·태그 후보를 제안한다.
- A-4. Document Knowledge: RULEBOOK/STORYBOOK/HANDOUT를 추출·청킹·임베딩·검색한다.

## 4. Ubiquitous Language and Terminology

- **Character Field**: 이름, 종족, 클래스, 배경, 능력치, 레벨 같은 캐릭터 생성 입력 항목.
- **Character Tag**: 필드의 구조, 선택지, 하위 항목, 입력 방식, 필수 여부를 표현하는 추출 결과.
- **Manual Input**: 근거가 부족하거나 사용자가 직접 입력해야 하는 값.
- **Select Option**: 문서 근거에서 추출된 선택지 중 사용자가 고르는 값.
- **Fixed Value**: 문서에서 확정된 기본값. 자동 입력되지만 사용자가 수정할 수 있다.
- **Evidence**: 문서 ID, extraction version, locator, 원문 인용, 유사도·신뢰도.
- **Conflict Review**: RULEBOOK/STORYBOOK/HANDOUT 근거가 서로 다른 값을 제시해 사용자 판단이 필요한 상태.

## 5. Core Use Cases

### UC-1. 컴파일 시 캐릭터 정보 검색

1. 컴파일러가 선택된 문서 묶음과 캐릭터 생성 query intent를 검색 컨텍스트에 전달한다.
2. RULEBOOK, STORYBOOK, HANDOUT를 각각 독립 검색한다.
3. 각 유형에서 유사도 하한을 넘은 근거만 수집한다.
4. 결과를 중복 제거·병합하고 캐릭터 AI 추출기에 전달한다.

### UC-2. 캐릭터 태그·필드 추출

1. AI 추출기가 근거에서 필드와 태그를 제안한다.
2. 각 결과에 입력 유형, 값·선택지, 필수 여부, 근거와 신뢰도를 붙인다.
3. 근거가 검증되지 않은 결과는 저장하지 않고 수동 입력으로 남긴다.

### UC-3. 블루프린트 보완 및 발행

1. 플레이어가 수동 필드 값을 입력한다.
2. 플레이어가 자동 추출에 없는 필드·태그·선택지를 추가한다.
3. 자동값은 수정할 수 있다.
4. 문서 간 충돌은 `CONFLICT_REVIEW`로 표시한다.
5. 필요한 보완이 끝나면 버전이 증가한 블루프린트를 발행한다.

## 6. Business Rules and Invariants

- BR-1. 캐릭터 정보 검색은 컴파일 시점에 수행한다.
- BR-2. RULEBOOK은 캐릭터 생성 정보 검색을 위해 RAG 인덱싱되어야 한다.
- BR-3. 검색은 문서 유형별로 독립 수행한다. 전역 top-k가 한 유형의 결과를 배제해서는 안 된다.
- BR-4. 검색 결과 선택 기준은 개수 우선이 아니라 문서 유형별 유사도 하한이다.
- BR-5. AI 전달량을 제한해야 할 경우 의미상 고정 개수가 아니라 시스템 token budget을 사용한다.
- BR-6. 검색 범위는 현재 Scenario Source Bundle의 문서와 extraction version으로 제한한다.
- BR-7. 근거가 충분하지 않은 필드는 `MANUAL_INPUT`으로 남긴다.
- BR-8. 추출된 선택지가 있으면 `SELECT_OPTION`으로 제공한다.
- BR-9. 확정된 문서 값은 `FIXED_VALUE`로 자동 입력하되 플레이어가 수정할 수 있다.
- BR-10. RULEBOOK/STORYBOOK/HANDOUT 간 값·선택지 충돌은 자동 병합하지 않고 `CONFLICT_REVIEW`로 남긴다.
- BR-11. 모든 자동 추출 결과는 검증 가능한 Evidence를 가져야 한다.
- BR-12. 개발 단계에서는 Evidence와 extraction 상태를 노출한다. 정식 사용자 화면에서는 숨길 수 있어야 한다.
- BR-13. 수동으로 추가·수정된 값과 태그는 블루프린트 revision에 저장한다.
- BR-14. 시나리오 판정·굴림 후보 검색은 캐릭터 정보 검색과 별도 query intent와 결과 계약을 유지한다.

## 7. States and State Transitions

### Character Field State

`UNRESOLVED → MANUAL_INPUT | SELECT_OPTION | FIXED_VALUE | CONFLICT_REVIEW`

사용자 보완 또는 충돌 해결 후 `READY`로 전환한다.

### Blueprint State

`NEEDS_REVIEW → READY → PUBLISHED`

수동 보완·충돌 해결·태그 추가는 revision을 증가시킨다.

## 8. Failures, Exceptions, and Boundary Conditions

- F-1. RULEBOOK이 RAG 인덱싱되지 않았으면 캐릭터 자동 추출을 완료할 수 없고 해당 근거가 없는 필드는 수동 입력으로 남긴다.
- F-2. 특정 문서 유형의 검색 결과가 없으면 다른 유형 검색을 중단하지 않는다.
- F-3. 유사도 하한을 넘는 근거가 없으면 해당 필드는 수동 입력으로 남긴다.
- F-4. AI 결과가 Evidence와 일치하지 않으면 결과를 폐기한다.
- F-5. 문서 간 충돌은 컴파일을 전체 실패시키지 않고 필드 단위 검토 상태로 남긴다.
- F-6. token budget을 초과할 경우 유사도·다양성 기준으로 근거를 줄이되 문서 유형별 최소 검색 기회를 보장한다.

## 9. Inputs and Outputs

### Input

- Scenario Source Bundle ID와 문서 ID 목록
- 문서별 `DocumentType`, extraction version
- query intent: `CHARACTER_CREATION`
- 문서 유형별 유사도 하한
- 시스템 token budget

### Output

- Character Field/Tag 후보
- 입력 유형: `MANUAL_INPUT`, `SELECT_OPTION`, `FIXED_VALUE`
- 값·선택지·필수 여부·수정 가능 여부
- Evidence와 개발용 신뢰도·상태
- 충돌·누락 진단
- revision 가능한 Character Creation Blueprint

## 10. Scope and Non-goals

### In Scope

- RULEBOOK RAG 청킹·임베딩·검색 활용
- 문서 유형별 독립 검색과 유사도 하한 정책
- 캐릭터 필드·태그 분류와 근거 검증
- 수동 보완, 선택지 추가, 값 수정, 충돌 검토
- 컴파일 시 캐릭터 정보 추출

### Non-goals

- 시나리오 Resolution Unit 검색 정책 변경
- 런타임 GM 검색 정책 변경
- 최종 사용자 화면에서 개발용 Evidence 노출
- 룰북의 규칙 자체를 변경하거나 새 규칙을 생성하는 것

## 11. Priorities and Trade-offs

- P0: 문서 유형별 독립 RAG 검색, RULEBOOK 인덱싱, 근거 검증, 수동 fallback
- P1: `MANUAL_INPUT`/`SELECT_OPTION`/`FIXED_VALUE` 분류, 충돌 검토, 수동 태그 추가
- P2: token budget 최적화, 검색 다양성 개선, 개발용 Evidence UI

정확한 근거와 문서 유형 간 공정한 검색 기회를 고정 결과 개수보다 우선한다. 다만 AI 요청은 운영상 token budget으로 제한한다.

## 12. Success Conditions and Acceptance Criteria

- AC-1. 실제 인덱싱된 RULEBOOK에서 종족·클래스·배경 선택지가 검색되고 블루프린트에 반영된다.
- AC-2. STORYBOOK 검색 결과가 많아도 RULEBOOK 검색이 실행되고 결과가 전달된다.
- AC-3. 한 문서 유형에 결과가 없어도 다른 문서 유형 결과로 추출을 계속한다.
- AC-4. 유사도 하한 미달 필드는 `MANUAL_INPUT`으로 표시된다.
- AC-5. 자동값은 사용자가 수정할 수 있고, 새 태그·선택지를 revision에 추가할 수 있다.
- AC-6. 문서 간 충돌은 `CONFLICT_REVIEW`로 표시되며 자동 병합되지 않는다.
- AC-7. 모든 자동 결과는 문서 ID·extraction version·locator·원문 인용을 가진다.
- AC-8. 기존 시나리오 Resolution Unit 추출 동작은 변경되지 않는다.
