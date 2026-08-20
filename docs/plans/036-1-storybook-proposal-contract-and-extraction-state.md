# 036-1 스토리북 제안 데이터 계약과 추출 상태

Issue: [#162](https://github.com/omegafrog/dnd-master/issues/162)
Parent: [#161](https://github.com/omegafrog/dnd-master/issues/161)
Status: `completed`
Dependencies: 없음

## 구현 목적

룰북 기본 스키마와 스토리북에서 추출된 추가 제안을 하나의 편집 필드로 섞지 않고, 검토 화면이 이해할 수 있는 명시적 데이터 계약으로 분리한다. 제안 없음, 추출 실패, 근거 부족을 서로 다른 상태로 제공해 UI가 진단 문자열을 추측하지 않도록 한다.

## 구현 범위

- `StorybookProposal`과 출처·원문 근거·결정 상태 계약
- 룰북 기본 스키마와 스토리북 제안이 포함된 검토 read model
- 제안 없음·추출 실패·근거 부족 상태
- 기존 `PlayPreparationView` 소비자와의 하위 호환
- 직렬화·조회·계약 테스트

## 의존성과 변경 경계

- 현재 `CharacterCreationBlueprint`의 단일 JSON 저장 경계를 조사하고 확장 지점을 정의한다.
- 제안 결정 명령의 실제 동작은 036-3에서 구현한다.
- 검토 화면 레이아웃은 036-2에서 구현한다.
- 실제 캐릭터 생성 입력과 계산 규칙은 변경하지 않는다.

## 테스트 계약

- 제안 출처·근거·상태 매핑 정책 단위 테스트
- 제안 없음·실패·근거 부족 API 계약 테스트
- review read model을 API에서 UI fixture까지 전달하는 `ui ~ entity` E2E

## 완료 조건

- UI가 diagnostics 문자열로 제안 여부를 추론하지 않는다.
- 룰북 기본 스키마와 스토리북 제안이 계약상 구분된다.
- 각 제안은 안정적인 ID와 출처 근거를 가진다.
- 제안 없음과 추출 실패가 구분된다.
- 의존성 없는 실행 가능 계획으로 유지한다.
