# 033-1 — 판본별 캐릭터 생성 계약

- Status: `completed`
- Issue: [#154](https://github.com/omegafrog/dnd-master/issues/154)
- Dependencies: 없음
- Parent: 032-5a Rulebook Template and Blueprint Layering의 후속 계약 정합성 수정

## 구현 목적

`DND_5E_2014`와 `DND_5E_2024`를 같은 D&D 5e 템플릿으로 처리하지 않는다. 캐릭터 생성의 선택값은 선택한 판본의 룰북 기반 canonical 계약에서만 나오며, OCR·RAG·AI 추출 결과는 그 계약을 바꾸지 못한다. 2024 룰북이 아직 게시되지 않은 상태에서는 2014 스키마로 폴백하지 않고 명시적으로 준비 중으로 차단한다.

## 문제와 근거

- `DndCharacterCreationTemplate.supportsDnd5e()`가 `DND_5E` 접두사를 모두 하나의 템플릿으로 처리한다.
- 블루프린트 병합은 RULEBOOK 추출값을 베이스 선택지에 합칠 수 있어 `Fighter`와 `파이터`, `Soldier`와 `군인`처럼 서로 다른 식별자가 공존할 수 있다.
- 추출기 결과에는 배경명 대신 `grizzled soldier` 같은 설명 문구가 포함될 수 있다.
- `DND_5E_2024` 카탈로그는 현재 `UNAVAILABLE`이므로 2024 규칙·스키마를 추정해서 제공하면 안 된다.

## 범위

### 1. 판본 계약 레지스트리

- 정확한 판본 ID로 조회하는 edition contract를 도입한다. 접두사 비교는 제거한다.
- 2014 계약은 D&D Basic Rules 2018 근거의 canonical ID·표시명·입력 모드·기본값을 소유한다.
- 2024 계약은 독립 슬롯으로 모델링한다. 게시·검증된 2024 게임 시스템 정의가 없으면 `UNAVAILABLE` 사유와 함께 블루프린트 생성을 거부한다.
- 계약은 필드 키, canonical 값, 표시명, 근거, 스키마 버전을 노출한다. 표시명은 번역될 수 있어도 저장 canonical ID를 대체하지 않는다.

### 2. 2014 베이스 스키마

- 종족, 클래스, 배경, 능력치 생성 방식, 표준 배열, 기술 선택, 장비 획득 및 시작 장비 조합을 2014 계약으로 정의한다.
- 현재 UI와 서버가 쓰는 한국어 canonical 값은 단일 shared mapping으로 통일한다.
- 종족·클래스·배경의 source evidence는 룰북 출처를 유지하되, 선택값은 계약에 있는 값만 허용한다.

### 3. 추출 결과 처리

- canonical 필드에 대한 RULEBOOK 후보는 값 집합을 확장하거나 대체하지 않는다.
- 동일 canonical 값으로 정규화 가능한 후보는 해당 값의 evidence/설명 보강으로만 사용한다.
- 정규화할 수 없는 후보와 STORYBOOK 후보는 `CONFLICT_REVIEW` 제안으로 격리한다.
- 배경의 성격·이상·유대·단점 표, 문장형 설명, 목차는 selectable background로 승격하지 않는다.

### 4. 버전과 호환성

- 새 초안/컴파일에만 신규 계약을 적용한다.
- 이미 게시된 블루프린트, 기존 세션의 blueprint revision, 기존 캐릭터 시트는 변경하지 않는다.
- API 응답에는 적용 판본과 계약/스키마 버전을 포함해 UI가 어떤 계약을 렌더링하는지 판별할 수 있게 한다.

## 수용 기준

1. `DND_5E_2014` 요청은 2014 canonical 선택지 하나만 반환한다.
2. `Fighter`, `Soldier`, `grizzled soldier` 같은 비-canonical 추출값은 2014 selectable 옵션에 포함되지 않는다.
3. `DND_5E_2024` 요청은 2014 템플릿을 반환하지 않으며, 2024 룰북이 준비되지 않았으면 명시적인 준비 불가 상태를 반환한다.
4. 2014 룰북의 근거와 storybook 제안은 provenance를 잃지 않는다.
5. 이미 잠긴 세션과 과거 블루프린트 리비전은 재해석되지 않는다.

## 테스트 계약

### 정책 단위 테스트

- 정확한 edition dispatch: 2014, 2024, 알 수 없는 판본.
- 2014 canonical contract의 종족·클래스·배경·능력치 배열.
- RULEBOOK 영문/서술형 후보가 canonical 옵션을 바꾸지 않는 정책.
- STORYBOOK 후보가 review 제안으로 격리되는 정책.
- 2024 정의 미게시 상태의 차단 정책.

### 통합·계약 테스트

- shared catalog 2014 룰북 + 3개 storybook 번들로 블루프린트 초안 생성.
- API 응답의 edition/contract version/provenance 확인.
- 과거 blueprint revision 조회가 새 계약으로 덮어써지지 않음.

### UI~엔티티 E2E 테스트

- 2014를 잠근 세션에서 canonical 2014 옵션만 내려오는지 검증한다.
- 2024를 선택했지만 카탈로그가 unavailable인 경우 생성 진입이 차단되는지 검증한다.

## 영향 영역

- `adventure-service` blueprint template/compiler/preparation API 및 관련 테스트
- `rule-knowledge-service` catalog definition lookup 계약(필요할 경우)
- 공개 blueprint view/DTO 및 web API 타입

## 범위 제외

- 2024 D&D 규칙·클래스·종족·장비를 추정하거나 하드코딩하는 작업
- 기존 세션·캐릭터의 일괄 마이그레이션
- AI가 추출한 storybook 선택지를 자동 채택하는 작업
