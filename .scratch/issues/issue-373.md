---
status: planned
source_issue: https://github.com/omegafrog/dnd-master/issues/373
---
## 상태

Planned

## 의존성

#363, #364

## 구현 목적

비전투 엔진이 사용 조건과 자원을 결정적으로 처리하고 환경 결과는 GM이 판정하게 한다.

## 범위

- 계획 식별자: plan-373.
- 능력 사용과 목표는 사용자가 선언한다. GM은 비전투 효과를 판정하지만 파티 행동이나 자원 소모를 임의로 시작하지 않는다.
- 해당 제품·구조 명세의 이 수용 기준에 직접 필요한 기존 화면, API, 정책, 저장소 경계만 수정한다.

## 수용 기준

- 요구사항 추적: AC-22, AC-39, UC-004.
- 소유 경계: 기존 비전투 능력 사용 경계와 Adventure Runtime; 시행 지점: 사용 조건·자원 확정과 GM 판정을 분리해 적용.
- 전투 조준·턴 실행 엔진을 적용하지 않고 능력의 저장된 간략 설명과 근거 위치를 GM에게 제공한다.
- 사용 가능 여부와 자원 비용은 비전투 엔진이 결정적으로 검증·기록한다.
- 사용이 확정되면 결과가 없더라도 비용을 한 번 차감한다.
- 대상이 지도 위치나 공간 범위를 요구하면 중앙 지도 UI에서 범위를 먼저 보여준다.
- 금지 사례: GM이 사용 가능 여부나 자원 비용을 대신 정하거나 전투 실행 엔진을 비전투 사용에 적용한다.

## 테스트 계약

- 개발용 진단 검증 (Architecture Spec §9.1·INV-01): 이 계획이 바꾸는 각 시작·완료·대체 경로·재시도·실패 분기에서 기존 `DevelopmentDiagnostics`(개발 진단 기능)와 `dev_` 개발 로그 형식을 확인한다. `ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED`(개발용 상세 진단 로그 설정)가 꺼졌을 때 상세 로그가 없고, 켜졌을 때 식별자·입력 크기/개수·기대/실제 값·선택 분기·소요 시간·실패 종류와 다음 복구 동작이 드러나며, 프롬프트·응답·인증 정보·불필요한 플레이어 입력·비공개 지도 정보를 기록하지 않고 실제 동작도 바꾸지 않는지 검사한다.

- 단위/정책: 전투/비전투 경계, 자원 확정, GM 판정 전달, 공간 범위 표시. 요구사항 식별자: AC-22, AC-39, UC-004. 소유 경계와 실행 진입점: 기존 비전투 능력 사용 경계와 Adventure Runtime; 사용 조건·자원 확정과 GM 판정을 분리해 적용. 추적 수용 조건: 전투 조준·턴 실행 엔진을 적용하지 않고 능력의 저장된 간략 설명과 근거 위치를 GM에게 제공한다. 반례: GM이 사용 가능 여부나 자원 비용을 대신 정하거나 전투 실행 엔진을 비전투 사용에 적용한다. 검증 사례: 단위/정책: 전투/비전투 경계, 자원 확정, GM 판정 전달, 공간 범위 표시.
- `ui ~ entity` E2E: 해당 없음 — 이 계획의 경계는 저비용 정책·단위·통합 검사로 확인하며, 실제 에이전트 포함 경로는 #369에서 한 번 검증한다.

## 관련 명세

- Product Spec: docs/specs/combat-movement-spell-range/product-spec.md
- Architecture Spec: docs/specs/combat-movement-spell-range/architecture-spec.md

## 다이어그램

- 제품: UC-004 환경 응용 흐름 — ![UC-004 환경 응용 흐름](https://github.com/omegafrog/dnd-master/blob/harness/spec-me/cff78917-af41-4451-87e5-df5ba5376d49/docs/specs/combat-movement-spell-range/diagrams/product/UC-004.activity.svg?raw=true)
