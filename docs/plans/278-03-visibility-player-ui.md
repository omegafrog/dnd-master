# Plan 278-03 — Visibility, Player Projection + UI

- Issue: [#291](https://github.com/omegafrog/dnd-master/issues/291)
- Status: Planned
- Dependencies: [#290](https://github.com/omegafrog/dnd-master/issues/290)

## 한국어 구현 목적

현재 시야와 탐험 영역을 분리하고, visibility 오류에서도 플레이어 정보가 fail-open되지 않게 한다. Combat Map부터 Web UI까지 공개 가능한 토큰·레이어·범례만 전달해 Fog of War 정보 누출을 막는다.

## 구현 범위

- finite `VisibilityProfile`
- `VisibilityPolicy`, `VisibilitySnapshot`, `PlayerSafeFogProjection`
- 이동·문 변경 후 current/explored 갱신
- fail-closed player-origin-only projection
- doors, last-seen, grid metadata transport contract
- hidden token/object/legend filtering
- thin black grid outline, compact Token Legend, `CombatMapView.tsx`

## 검증 계약

- 정책 단위 테스트: bounded visibility, explored transition, fail-closed projection, hidden token filtering, legend derivation
- `ui ~ entity` E2E: 이동·문 변경 후 fog/explored/legend 갱신과 숨겨진 토큰 비노출 확인

## 관련 명세

- [Product Spec](../specs/278/product-spec.md)
- [Architecture Spec](../specs/278/architecture-spec.md)

## 다이어그램

해당 없음 — ticket-scoped SVG 없음. PlantUML renderer 누락으로 `.puml`만 존재.
