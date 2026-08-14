# 036-3 스토리북 제안 사용·제외 흐름

Issue: [#164](https://github.com/omegafrog/dnd-master/issues/164)
Parent: [#161](https://github.com/omegafrog/dnd-master/issues/161)
Status: `planned`
Dependencies: 036-1, 036-2

## 구현 목적

스토리북에서 발견된 항목을 사용자가 제안별로 판단할 수 있게 한다. 룰북 기본 스키마는 항상 유지하고, 사용하기로 결정한 제안만 적용 projection에 포함한다.

## 구현 범위

- `UNDECIDED`, `APPLIED`, `EXCLUDED`, `NEEDS_EVIDENCE` 상태
- 제안별 사용하기·제외하기 명령과 저장
- 근거 없는 제안 사용 차단
- revision 충돌과 재조회
- 적용·제외 결과 요약

## 의존성과 변경 경계

- 036-1의 제안 ID·근거·read model을 사용한다.
- 036-2의 카드 UI에 결정을 연결한다.
- 게시와 캐릭터 생성 진입은 036-4에서 최종 차단한다.
- 룰북 기본 스키마를 제안 결정의 mutation 대상으로 만들지 않는다.

## 테스트 계약

- 결정 전이와 근거 검증 정책 단위 테스트
- 명령·revision 충돌 API 계약 테스트
- 사용/제외 결정이 적용 projection에 반영되는 `ui ~ entity` E2E

## 완료 조건

- 모든 제안이 결정 상태를 가진다.
- 사용하기는 근거가 있는 제안에만 가능하다.
- 제외한 제안은 적용 projection에 포함되지 않는다.
- 미결정 수와 적용 결과가 화면에 표시된다.
