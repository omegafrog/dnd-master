# 033-2 — 판본 계약 기반 UI·시트 검증 통합

- Status: `completed`
- Issue: [#155](https://github.com/omegafrog/dnd-master/issues/155)
- Dependencies: [033-1](033-1-edition-specific-character-creation-contract.md)

## 구현 목적

캐릭터 생성 UI, 저장 요청, 캐릭터 관리 검증기, 계산 결과가 세션에 잠긴 동일 판본 계약을 사용하게 한다. 2014 UI는 2014 canonical 값만 저장하고, 서버는 선택값과 시작 장비 조합을 검증한 뒤 파생 수치를 계산한다. 2024 계약이 준비되지 않은 동안은 2014로 저장되지 않는다.

## 범위

### 1. 세션-블루프린트-UI 연결

- 캐릭터 생성 화면은 세션의 잠긴 edition, blueprint revision, contract version을 읽는다.
- 하드코딩된 `edition: "DND_5E_2014"` 제출값을 제거하고 잠긴 판본을 사용한다.
- UI의 종족·클래스·배경·하위 선택·장비 옵션은 blueprint의 canonical 계약에서 렌더링한다.
- 선택 불가 2024 판본은 이유와 룰북 준비 상태를 표시하고 저장 버튼을 비활성화한다.

### 2. 2014 저장·검증 계약

- 2014 새 시트 생성 시 race/class/background가 2014 canonical 집합에 속하는지 검증한다.
- 표준 배열, 종족 보정, 클래스 기술 선택 수, 1레벨 주문 요구사항을 계속 서버가 판정한다.
- 클래스/배경 시작 장비 선택은 실제 허용 조합인지 검증한다.
- 장착 장비의 소유, 방패, 양손 장비 충돌, 갑옷 숙련 검증은 시작 장비 결과와 함께 일관되게 검사한다.
- 오류 응답은 UI가 필드별로 표시할 수 있는 안정적인 위반 코드와 계약 판본을 포함한다.

### 3. 2024 안전 차단

- 2024 전용 validator가 없으면 시트 생성 API는 `EDITION_CONTRACT_UNAVAILABLE`로 거부한다.
- UI, 내부 생성 API, AI 동료 생성 경로 모두 동일한 차단 규칙을 사용한다.

### 4. 호환성

- 기존 2014 시트의 조회와 기존 세션의 플레이는 유지한다.
- 이전 방식으로 저장된 2014 시트 수정 시에는 데이터 손실 없이 계약 호환 검증 또는 명시적 보정 안내를 적용한다.

## 수용 기준

1. 2014 세션의 UI 선택과 POST 본문이 `인간 / 파이터 / 군인` 같은 동일 canonical 값을 사용한다.
2. 인간 파이터 군인, 표준 배열, 체인 메일·방패·롱소드·라이트 크로스보우·던전 탐험가 팩 시트가 서버 검증을 통과하며 AC 18, HP 12, 롱소드 `+5 / 1d8+3`으로 계산된다.
3. 설명 문구 배경, canonical 밖의 영문 값, 잘못된 장비 조합은 서버가 거부한다.
4. 2024 선택은 2014 validator나 2014 UI로 폴백하지 않는다.
5. 유저 조작 캐릭터 생성 → 파티 연결까지 E2E로 통과한다.

## 테스트 계약

### 정책 단위 테스트

- 2014 canonical enum/value 검증.
- 시작 장비 선택 조합 검증: 허용/중복/누락/비숙련 갑옷/양손+방패.
- 2024 계약 미준비 시 생성 거부.

### API 통합 테스트

- 2014 유효 시트 생성·조회·갱신의 파생 수치 일치.
- 비-canonical race/class/background와 불완전 장비의 오류 코드.
- edition contract가 누락되거나 blueprint와 불일치할 때의 거부.

### UI~엔티티 E2E 테스트

- 잠긴 2014 blueprint을 렌더링하고 유효 캐릭터를 생성한 뒤 DIRECT 파티원으로 연결.
- 2024 unavailable 흐름에서 입력과 저장이 차단됨.

## 영향 영역

- `web-ui` CharacterSheetCreatorView, CharacterCreationPage, API 타입/테스트, Playwright E2E
- `character-management-service` 시트 생성/갱신 validator, 파생 계산, 계약 오류 DTO, 테스트
- `adventure-service` 세션 blueprint binding 노출 및 내부 시트 생성 계약

## 완료 근거

- 블루프린트 provenance와 `adventure_session.character_edition`에 판본을 저장하고, 캐릭터 생성 UI는 세션에 고정된 값을 우선 사용한다.
- 2014 기본 선택값과 파이터·로그·위저드·클레릭 시작 장비 조합은 서버에서 검증한다.
- 2024 블루프린트는 생성 단계에서 `UNAVAILABLE`이며, UI 저장도 비활성화된다.
- adventure-service, character-management-service 및 캐릭터 생성 UI 회귀 테스트·production build를 통과했다.

## 범위 제외

- 2024 룰북 기반 실제 캐릭터 생성 규칙 구현
- 캐릭터 시트 레이아웃 전체 재디자인
- 기존 캐릭터 자동 변환
