# Plan 278-01 — Map Preparation + Grid Calibration

- Issue: [#289](https://github.com/omegafrog/dnd-master/issues/289)
- Status: Planned
- Dependencies: 없음

## 한국어 구현 목적

맵 원본에서 실제 플레이 영역과 좌표계를 결정한다. Printed Grid를 신뢰할 수 있을 때 보존·정렬하고, 그 외에는 명시적인 FALLBACK grid를 생성해 이후 활성화와 UI가 안정적인 좌표를 사용하게 한다.

## 구현 범위

- `MapContentBoundsDetector`와 보수적 bounds normalization
- `MapGridDetector` candidate와 `PrintedGridAcceptancePolicy`
- `FallbackGridPolicy`, `GridSource`, grid metadata
- `MapPreparationPipeline` 및 unreadable source 실패
- 대형 Printed Grid 보존

## 검증 계약

- 정책 단위 테스트: bounds confidence, Printed/FALLBACK 분기, large-grid preservation, determinism, unreadable source
- `ui ~ entity` E2E: 대표 맵 준비 후 플레이 화면에서 grid source, bounds, alignment 확인

## 관련 명세

- [Product Spec](../specs/278/product-spec.md)
- [Architecture Spec](../specs/278/architecture-spec.md)

## 다이어그램

해당 없음 — ticket-scoped SVG 없음. PlantUML renderer 누락으로 `.puml`만 존재.
