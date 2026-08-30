# TT-006: Phase-Based Preparation Progress

- Issue: [#243](https://github.com/omegafrog/dnd-master/issues/243)
- Status: `planned`
- Dependencies: TT-005 `completed`
- Parent: [#237](https://github.com/omegafrog/dnd-master/issues/237)

## 구현 목적

Backend phase, completed units, optional total units를 progress 정본으로 사용한다. UI는 total을 알 때만 percentage를 계산하고 모르면 indeterminate 상태를 표시한다.

## Implementation Scope

- `PreparationProgress` value object와 additive tactical job schema
- `TacticalPreparationReadModel` player/internal projection 분리
- API nullable total/percentage contract
- `AdventureSessionApi` type와 React progress rendering 변경
- legacy integer progress compatibility

## Acceptance Criteria

- total known → derived percentage
- total unknown/zero → percentage null, indeterminate UI
- fixed 70% 제거
- internal failure detail 비노출

## Test Contract

- Policy unit: phase/count validation과 percentage projection
- React unit: determinate/indeterminate/accessibility states
- UI ~ entity E2E: durable job counts → API → rendered progress/state

## Implementation Boundaries

- TT-005 job identity/worker 재사용
- rule-knowledge async schema를 tactical contract로 오용하지 않음
