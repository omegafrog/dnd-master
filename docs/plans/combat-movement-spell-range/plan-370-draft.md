---
status: planned
source_issue: https://github.com/omegafrog/dnd-master/issues/370
---
## 상태

Planned

## 의존성

#369

## 구현 목적

중앙 지도 화면의 확대·축소와 지면 높이 표현을 포인터·범위 오버레이에 맞춘다.

## 범위

- `workspace_root`: `/home/jiwoo/workspace/.harness-worktrees/dnd-master/35f35f96-87e3-4e88-8277-f6e5e0aa8a5b`
- `workspace_branch`: `harness/implement-combat-movement-spell-range/35f35f96-87e3-4e88-8277-f6e5e0aa8a5b`

- 계획 식별자: plan-370.
- 해당 제품·구조 명세의 이 수용 기준에 직접 필요한 기존 화면, API, 정책, 저장소 경계만 수정한다.

## 수용 기준

- 요구사항 추적: AC-17, AC-18, BR-07, BR-20, EX-09, EX-11, G-06, UC-003.
- 소유 경계: Combat Map이 전투 토큰의 지도 위치와 저장된 지면 높이를 소유한다. `CombatMapView`는 이를 표시·투영하며 정본을 따로 만들지 않는다. 시행 지점: 서버의 공개 지도·토큰 자료와 미리보기·실행의 같은 높이 거리 계산.
- 지도 화면은 모험 UI의 중앙 작업 영역에 두고 주변 패널은 지도를 가리지 않도록 배치한다.
- 확대·축소 후 포인터, 격자, 토큰과 오버레이가 같은 지도 좌표를 가리킨다.
- 전투 토큰의 지면 높이는 Combat Map에 저장·조회하고 미리보기와 실행이 같은 값을 사용한다. 거리 칸 수는 `max(max(abs(dx), abs(dy)), ceil(abs(높이 차이) / 한 칸 거리))`로 계산한다. 높이 자료가 없는 벽과 닫힌 문은 모든 높이에서 막는다.
- 숨겨진 벽과 지도 상태는 공개 범위에 따라 보인다.
- 금지 사례: 확대·축소 뒤 포인터·토큰 오버레이가 다른 위치를 가리키거나 숨겨진 벽을 드러낸다. 높이 차이를 더하거나 내림 처리해 거리 칸을 잘못 계산하거나, 높이 자료가 없는 벽·닫힌 문을 통과한다.

## 테스트 계약

- 개발용 진단 검증 (Architecture Spec §9.1·INV-01): 이 계획이 바꾸는 각 시작·완료·대체 경로·재시도·실패 분기에서 기존 `DevelopmentDiagnostics`(개발 진단 기능)와 `dev_` 개발 로그 형식을 확인한다. `ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED`(개발용 상세 진단 로그 설정)가 꺼졌을 때 상세 로그가 없고, 켜졌을 때 식별자·입력 크기/개수·기대/실제 값·선택 분기·소요 시간·실패 종류와 다음 복구 동작이 드러나며, 프롬프트·응답·인증 정보·불필요한 플레이어 입력·비공개 지도 정보를 기록하지 않고 실제 동작도 바꾸지 않는지 검사한다.

- 단위/정책: 확대 배율별 좌표 변환과 화면 배치, Combat Map 저장·조회 값이 전투 토큰의 지면 높이 정본인지, 미리보기와 실행이 같은 높이 값을 쓰는지 확인한다. 거리 칸 수가 가로·세로 칸 차이 중 큰 값과 높이 차이를 한 칸 거리로 나누어 올림한 값 중 큰 값인지 검사한다. 예: 가로·세로 거리 2칸/높이 5피트/칸 거리 5피트는 2칸, 가로·세로 거리 1칸/높이 차이 11피트/칸 거리 5피트는 3칸이다. 높이 자료가 없는 벽·닫힌 문은 높이에 관계없이 막히며, 공개 범위를 넘는 지도 상태는 표시하지 않는지 확인한다. 요구사항 식별자: AC-17, AC-18, BR-07, BR-20, EX-09, EX-11, G-06, UC-003.
- `ui ~ entity` E2E: 해당 없음 — 이 계획의 경계는 저비용 정책·단위·통합 검사로 확인하며, 실제 에이전트 포함 경로는 #369에서 한 번 검증한다.

## 관련 명세

- Product Spec: docs/specs/combat-movement-spell-range/product-spec.md
- Architecture Spec: docs/specs/combat-movement-spell-range/architecture-spec.md

## 다이어그램

- 제품: UC-002 미리보기와 실행 — ![UC-002 미리보기와 실행](https://github.com/omegafrog/dnd-master/blob/harness/spec-me/cff78917-af41-4451-87e5-df5ba5376d49/docs/specs/combat-movement-spell-range/diagrams/product/UC-002.activity.svg?raw=true)
