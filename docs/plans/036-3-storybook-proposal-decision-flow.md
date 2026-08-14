# 036-3 스토리북 제안 적용·제외 흐름

Issue: [#164](https://github.com/omegafrog/dnd-master/issues/164)
Parent: [#161](https://github.com/omegafrog/dnd-master/issues/161)
Status: `planned`
Dependencies: 036-1, 036-2

## 구현 목적

사용자가 각 스토리북 제안을 적용하거나 제외하고, 적용한 제안만 캐릭터 생성 설정에 포함되도록 결정 상태와 저장 흐름을 구현한다.

## 구현 범위

- `UNDECIDED`, `APPLIED`, `EXCLUDED`, `NEEDS_EVIDENCE` 결정 상태
- 제안별 적용·제외 조작 및 저장 상태
- 근거가 없는 제안 적용 차단
- 적용된 설정 projection 생성과 재조회 일관성
- revision 충돌, 저장 실패, 재시도 처리
- 필요한 backend domain/application/API 연계

## 의존성과 변경 경계

- 036-1의 제안 계약과 036-2의 화면 컴포넌트를 사용한다.
- 게시 전 검증과 세션 진입은 036-4에서 최종 연결한다.
- 기존 per-field revision 경계를 유지한다.

## 테스트 계약

- `StorybookProposal` 및 decision policy 단위 테스트
- apply/exclude API 계약 테스트
- 적용·제외 후 applied projection과 revision을 확인하는 UI~entity E2E
- 근거 부족 제안과 revision conflict 회귀 테스트

## 완료 조건

- 적용하지 않은 제안이 applied projection에 들어가지 않는다.
- 제외한 제안은 게시 대상에 포함되지 않는다.
- 근거 부족 제안은 적용할 수 없다.
- 새로고침 후에도 결정 상태가 유지된다.
