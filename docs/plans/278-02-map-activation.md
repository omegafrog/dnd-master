# Plan 278-02 — Map Activation + Player Spawn

- Issue: [#290](https://github.com/omegafrog/dnd-master/issues/290)
- Status: Planned
- Dependencies: [#289](https://github.com/omegafrog/dnd-master/issues/289)

## 한국어 구현 목적

맵 준비와 런타임 진입을 분리한다. 현재 진입 맥락에 맞는 유효한 PLAYER Spawn을 결정하고, PLAYER·visibility·map version·active relation을 하나의 원자적 활성화로 저장해 잘못된 시작 위치와 partial commit을 막는다.

## 구현 범위

- `MapActivationContext`, `SpawnResolutionPolicy`, `SpawnResolution`
- explicit anchor → entry candidate → deterministic safe fallback
- grid 내부·통행 가능·미점유 셀 검증
- `(0,0)` 암묵 기본값 제거
- PLAYER 배치와 최초 visibility 계산
- atomic activation, rollback, `NO_VALID_PLAYER_SPAWN`, version conflict

## 검증 계약

- 정책 단위 테스트: Spawn 우선순위와 candidate 검증, occupied/blocked/out-of-grid, safe fallback, no-valid-spawn
- `ui ~ entity` E2E: 진입 맥락으로 활성화 후 플레이 화면 PLAYER 위치와 초기 공개 영역 확인

## 관련 명세

- [Product Spec](../specs/278/product-spec.md)
- [Architecture Spec](../specs/278/architecture-spec.md)

## 다이어그램

[Map Activation Activity SVG](../specs/278/diagrams/product/UC-4-map-activation.activity.svg) · [Runtime State SVG](../specs/278/diagrams/architecture/combat-map-runtime.state.svg) · [Activation/Visibility Sequence SVG](../specs/278/diagrams/architecture/map-activation-visibility.sequence.svg)
