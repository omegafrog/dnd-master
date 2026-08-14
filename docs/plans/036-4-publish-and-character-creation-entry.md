# 036-4 검토 완료·게시·캐릭터 생성 연계

Issue: [#165](https://github.com/omegafrog/dnd-master/issues/165)
Parent: [#161](https://github.com/omegafrog/dnd-master/issues/161)
Status: `planned`
Dependencies: 036-1, 036-2, 036-3

## 구현 목적

미완료 필수 항목이나 미결정 스토리북 제안이 있으면 게시를 막고, 적용된 설정만 게시한 뒤 실제 캐릭터 생성 화면으로 이동하게 한다.

## 구현 범위

- 필수 룰북 항목과 미결정 제안 검증
- 게시 전 누락 항목 및 미결정 제안 안내
- applied projection만 게시 결과에 반영
- 게시 완료 후 세션/캐릭터 생성 진입
- 도메인 불변식, UI 버튼 상태, 재시도 가능한 오류

## 의존성과 변경 경계

- 036-1~036-3의 계약과 상태를 통합한다.
- 기존 `/sessions/{sessionId}/character-blueprint`와 캐릭터 생성 화면을 유지한다.
- 캐릭터 시트 계산 규칙과 실제 플레이 화면은 변경하지 않는다.

## 테스트 계약

- publish precondition 및 session boundary 단위 테스트
- 게시 API 계약 테스트
- 미결정→적용/제외→게시→캐릭터 생성 전체 UI~entity E2E
- 게시된 설정의 항목 집합이 applied projection과 일치하는지 검증

## 완료 조건

- 필수 항목이 미완료면 게시할 수 없다.
- 미결정 제안이 있으면 게시할 수 없다.
- 게시 결과에 적용된 제안만 포함된다.
- 게시 완료 전에 캐릭터 생성 진입 버튼이 노출되지 않는다.
