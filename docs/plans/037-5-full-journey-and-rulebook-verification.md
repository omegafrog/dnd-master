# 037-5 전체 흐름 검증과 데이터 대조

Issue: [#173](https://github.com/omegafrog/dnd-master/issues/173)
Parent: [#168](https://github.com/omegafrog/dnd-master/issues/168)
Status: `planned`
Dependencies: 037-4

## 구현 목적

최종 사용자 흐름에서 룰북 선택지와 설명이 실제 `dnd5th.pdf`와 일치하는지 검증하고, 자료 준비부터 캐릭터 생성까지의 회귀를 막는다. 기존 캐릭터·모험 데이터와 CI도 함께 확인한다.

## 구현 범위

- `dnd5th.pdf`와 카탈로그 필드·선택지·설명·출처 대조
- potent 시나리오 3개 PDF를 포함한 모험 준비 → 캐릭터 스키마 흐름
- 기존 저장 데이터 compatibility test
- 5e/5.5e availability와 unsupported 상태 검증
- 최신 Playwright 스크린샷 및 PR 증거 갱신

## 의존성과 변경 경계

- 037-1~037-4의 모든 API/UI 계약을 검증한다.
- 룰북 원문 자체는 수정하지 않는다.
- 불일치가 발견되면 원문 근거가 확인된 항목만 수정하고, 확인 불가 항목은 blocker로 남긴다.

## 테스트 계약

- 전체 backend/frontend unit 및 integration test
- typecheck, lint, build
- 실제 백엔드 기반 전체 Playwright journey
- 최종 `ui ~ entity` E2E에서 모험 생성과 캐릭터 생성 진입 확인

## 완료 조건

- 룰북 대조 결과와 예외가 문서화된다.
- 전체 테스트와 CI가 통과한다.
- 최신 접힌/펼친 스키마 화면 스크린샷이 PR에 반영된다.
