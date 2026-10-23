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

- 계획 식별자: plan-370.
- 해당 제품·구조 명세의 이 수용 기준에 직접 필요한 기존 화면, API, 정책, 저장소 경계만 수정한다.

## 수용 기준

- 요구사항 추적: AC-17, AC-18, BR-07, BR-20, EX-09, EX-11, G-06, UC-003.
- 소유 경계: 전투 지도 좌표와 지도 화면(CombatMapView); 시행 지점: 화면 포인터·오버레이를 현재 확대 및 지면 높이 계산에 맞춰 투영.
- 지도 화면은 모험 UI의 중앙 작업 영역에 두고 주변 패널은 지도를 가리지 않도록 배치한다.
- 확대·축소 후 포인터, 격자, 토큰과 오버레이가 같은 지도 좌표를 가리킨다.
- 비행 높이는 지면으로부터의 수치로 표현해 거리·장애물·효과 포함 여부에 사용한다.
- 숨겨진 벽과 지도 상태는 공개 범위에 따라 보인다.
- 금지 사례: 확대·축소한 뒤 포인터·토큰 오버레이가 지도와 다른 위치를 가리키거나 숨겨진 벽을 드러낸다.

## 테스트 계약

- 개발용 진단 검증 (Architecture Spec §9.1·INV-01): 이 계획이 바꾸는 각 시작·완료·대체 경로·재시도·실패 분기에서 기존 `DevelopmentDiagnostics`(개발 진단 기능)와 `dev_` 개발 로그 형식을 확인한다. `ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED`(개발용 상세 진단 로그 설정)가 꺼졌을 때 상세 로그가 없고, 켜졌을 때 식별자·입력 크기/개수·기대/실제 값·선택 분기·소요 시간·실패 종류와 다음 복구 동작이 드러나며, 프롬프트·응답·인증 정보·불필요한 플레이어 입력·비공개 지도 정보를 기록하지 않고 실제 동작도 바꾸지 않는지 검사한다.

- 단위/정책: 확대 배율별 좌표 변환, 화면 배치 상태, 높이 기반 거리·공개 경계. 요구사항 식별자: AC-17, AC-18, BR-07, BR-20, EX-09, EX-11, G-06, UC-003. 소유 경계와 실행 진입점: 전투 지도 좌표와 지도 화면(CombatMapView); 화면 포인터·오버레이를 현재 확대 및 지면 높이 계산에 맞춰 투영. 추적 수용 조건: 지도 화면은 모험 UI의 중앙 작업 영역에 두고 주변 패널은 지도를 가리지 않도록 배치한다. 반례: 확대·축소한 뒤 포인터·토큰 오버레이가 지도와 다른 위치를 가리키거나 숨겨진 벽을 드러낸다. 검증 사례: 단위/정책: 확대 배율별 좌표 변환, 화면 배치 상태, 높이 기반 거리·공개 경계.
- `ui ~ entity` E2E: 해당 없음 — 이 계획의 경계는 저비용 정책·단위·통합 검사로 확인하며, 실제 에이전트 포함 경로는 #369에서 한 번 검증한다.

## 관련 명세

- Product Spec: docs/specs/combat-movement-spell-range/product-spec.md
- Architecture Spec: docs/specs/combat-movement-spell-range/architecture-spec.md

## 다이어그램

- 제품: UC-002 미리보기와 실행 — ![UC-002 미리보기와 실행](https://github.com/omegafrog/dnd-master/blob/harness/spec-me/cff78917-af41-4451-87e5-df5ba5376d49/docs/specs/combat-movement-spell-range/diagrams/product/UC-002.activity.svg?raw=true)
