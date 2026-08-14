# 036-1 스토리북 제안 데이터 계약과 추출 상태

Issue: [#162](https://github.com/omegafrog/dnd-master/issues/162)
Parent: [#161](https://github.com/omegafrog/dnd-master/issues/161)
Status: `ready-for-agent`
Dependencies: none

## 구현 목적

룰북 기본 항목과 스토리북에서 추출한 추가 제안을 API에서 분리한다. UI가 진단 문자열을 추측하지 않고, 제안의 출처·근거·적용 가능 여부·추출 상태를 안정적으로 표시할 수 있게 한다.

## 구현 범위

- `StorybookProposalView`와 제안 결정 상태 계약 추가
- 출처 문서, 설명, 원문 인용, evidence, 근거 부족 상태 제공
- preparation API 및 `SetupApi` 타입/어댑터 확장
- 제안 없음·추출 실패·근거 부족을 구분하는 상태 모델
- 기존 blueprint 저장 구조와 호환되는 read model/projection 유지

## 의존성과 변경 경계

- 기존 `PlayPreparationView`와 `CharacterCreationBlueprintView`를 깨지 않는다.
- 실제 적용/제외 저장은 036-3에서 구현한다.
- 화면 레이아웃은 036-2에서 구현한다.

## 테스트 계약

- `ScenarioPreparationApplicationService` 및 controller 정책 단위 테스트
- `SetupApi` 요청/응답 계약 테스트
- 룰북·스토리북 projection을 준비 API에서 받아 표시 모델로 변환하는 UI~entity E2E
- 제안 없음, 추출 실패, 근거 부족 fixture를 각각 검증

## 완료 조건

- 룰북 기본 fields와 storybook proposals가 별도 컬렉션으로 반환된다.
- 각 제안이 source document, description, quote/evidence, applyability를 가진다.
- 빈 결과와 실패 결과가 서로 다른 상태로 표현된다.
- 첫 번째 구현 가능한 슬라이스이므로 `ready-for-agent` 상태를 유지한다.
