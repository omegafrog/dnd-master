# 037-3 모험 준비와 카탈로그 통합

Issue: [#171](https://github.com/omegafrog/dnd-master/issues/171)
Parent: [#168](https://github.com/omegafrog/dnd-master/issues/168)
Status: `planned`
Dependencies: 037-1, 037-2

## 구현 목적

모험 준비 화면의 룰북 기본 스키마를 별도 하드코딩 템플릿이 아니라 선택된 catalog revision에서 제공한다. 스토리북 추출 제안과 룰북 기본 스키마를 분리하면서도 최종 캐릭터 생성 설정에는 동일한 기본 스키마가 사용되게 한다.

## 구현 범위

- scenario package의 선택 룰북 catalog revision lock
- `play-preparation`의 `baseSchema`에 전체 fields/options/optionDetails/source/evidence/revision 제공
- storybook proposals와 catalog fields의 명시적 분리
- catalog 없음·리비전 충돌·스토리북 제안 0개 상태 처리
- 기존 `DndCharacterCreationTemplate`의 중복 데이터 제거 seam

## 의존성과 변경 경계

- 037-2의 canonical catalog API를 사용한다.
- review 화면 렌더링과 선택지 UI는 037-4에서 수행한다.
- 스토리북 추출 알고리즘 자체는 변경하지 않는다.

## 테스트 계약

- preparation application service의 catalog projection unit test
- base schema와 storybook proposal 분리 contract test
- 모험 준비 → character blueprint API를 통과하는 `ui ~ entity` E2E

## 완료 조건

- `play-preparation` 응답에 catalog revision과 모든 선택지가 있다.
- 룰북 기본 필드는 항상 포함되고 스토리북 제안과 구분된다.
- catalog 불일치 시 캐릭터 생성 진입이 차단된다.
