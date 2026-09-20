# 공간 이동 해결 구현 계획

- 계획 세트: `spatial-movement-resolution`
- 상위 이슈: [#334 — 공개 정보 기반 이동 제안과 재시작 가능한 칸별 이동 해결](https://github.com/omegafrog/dnd-master/issues/334)
- 상태 원본: GitHub Project #5의 `Workflow Status`
- 기준 브랜치: `spec/spatial-movement-resolution`
- 고정 기준 커밋: `0042ec5712a47f6c5108ae068935d1c959773eb7`
- 계획 브랜치: `plan/spatial-movement-resolution`

## 구현 조각

| 순서 | 하위 이슈 | 구현 요약 | 선행 이슈 |
|---|---|---|---|
| 1 | [#327](https://github.com/omegafrog/dnd-master/issues/327) | 근거 기반 공간 요소 준비와 전투 지도 정본 저장 | 없음 |
| 2 | [#328](https://github.com/omegafrog/dnd-master/issues/328) | 직접 드래그 이동 제안과 공개 정보 기반 서버 경로 미리보기 | #327 |
| 3 | [#329](https://github.com/omegafrog/dnd-master/issues/329) | 재시작 가능한 이동 예약과 원자적 칸별 최종 반영 | #327, #328 |
| 4 | [#330](https://github.com/omegafrog/dnd-master/issues/330) | 공간 탐지·관찰·발동과 플레이어 판정 대기 재개 | #327, #329 |
| 5 | [#331](https://github.com/omegafrog/dnd-master/issues/331) | 적의 인지·전투 이동 제한·후속 모험 진행 | #330 |
| 6 | [#332](https://github.com/omegafrog/dnd-master/issues/332) | 자연어 목적지 해석과 재접속 가능한 이동 확인 | #328, #329 |
| 7 | [#333](https://github.com/omegafrog/dnd-master/issues/333) | 토큰 시각 상태와 번들 자산 라이선스 추적 | #328 |

핵심 실행 순서는 `#327 → #328 → #329 → #330 → #331`이다. #332는 #329 뒤에, #333은 #328 뒤에 실행할 수 있다. #330과 #332가 같은 Adventure API·지도 화면 파일을 수정하면 순차 실행한다.

## 관련 명세

- [Product Spec](../../specs/spatial-movement-resolution/product-spec.md)
- [Architecture Spec](../../specs/spatial-movement-resolution/architecture-spec.md)
- [ADR-018](../../adr/ADR-018-staged-combat-map-movement-resolution.md)

## 다이어그램

- Product
  - [이동 제안 확인](../../specs/spatial-movement-resolution/diagrams/product/UC-1-movement-proposal.usecase.svg)
  - [칸별 이동 해결](../../specs/spatial-movement-resolution/diagrams/product/UC-2-movement-resolution.activity.svg)
  - [공간 요소 준비](../../specs/spatial-movement-resolution/diagrams/product/UC-3-spatial-feature-preparation.activity.svg)
  - [공간 요소 업무 상태](../../specs/spatial-movement-resolution/diagrams/product/spatial-feature.business-state.svg)
- Architecture
  - [전투 지도 공간 이동 구조](../../specs/spatial-movement-resolution/diagrams/architecture/combat-map-spatial-resolution.class.svg)
  - [이동 예약 설계 상태](../../specs/spatial-movement-resolution/diagrams/architecture/movement-resolution-operation.state.svg)

각 하위 이슈의 전체 범위·수용 기준·정책 단위 테스트·`ui ~ entity` E2E 계약은 해당 GitHub Issue 본문이 정본이다.
