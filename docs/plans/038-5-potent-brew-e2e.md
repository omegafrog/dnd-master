# 038-5 Potent Brew 전술 장면 전체 흐름 검증

- Issue: [#178](https://github.com/omegafrog/dnd-master/issues/178)
- Parent: [#179](https://github.com/omegafrog/dnd-master/issues/179)
- 상태: `blocked`
- 의존성: `038-1`, `038-2`, `038-3`, `038-4`

## 구현 목적

실제 Potent Brew 시나리오 자료를 사용해 모험 계획 생성부터 전술 맵 활성화, 숨김 투영, 트리거, 미래 단계 개정까지 제품 흐름을 검증한다.

## 범위

- fresh Playwright journey와 backend E2E fixture를 작성한다.
- 자료 인용, 보스·보상·전투 진입 트리거, 안개, 플레이어 비공개, 분기·개정을 검증한다.

## 비범위

- fixture-only 성공을 제품 근거로 삼지 않으며 실제 준비된 Potent Brew 자료를 사용한다.

## 완료 기준

- Potent Brew 전술 단계는 근거가 연결된 완결 장면으로 생성된다.
- 플레이어 화면에는 숨겨진 좌표·토큰·트리거가 없다.
- 계획된 트리거와 미공개 미래 단계 개정이 재현되고, 공개 단계는 보존된다.

## 테스트 계약

- backend E2E: fixture와 실제 포트 응답에서 generation/retry/activation/trigger/revision 계약을 검증한다.
- Playwright: 자료 선택 → 준비 → 계획 → 전술 맵 → 플레이어 projection → 트리거 → 미래 revision 전체 흐름을 독립 실행한다.
- 회귀: 테스트는 고정된 UI fixture만 통과하는 대신 생성된 entity와 API projection을 직접 검증한다.
