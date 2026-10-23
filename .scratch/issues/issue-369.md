---
status: planned
source_issue: https://github.com/omegafrog/dnd-master/issues/369
---
## 상태

Planned

## 의존성

#368

## 구현 목적

전투 이동 중 GM 판정이 필요한 사건을 처리하면서 이미 이동한 위치·이동량을 보존하고 같은 플레이어 차례에서 다음 행동을 기다리는 기존 이동 흐름을 구현한다.

## 범위

- 계획 식별자: plan-369.
- 사용자는 경로를 확인한다. Combat Map은 칸별 이동을 검증하고 실제 통과 경로·비용·중단 원인을 Adventure Runtime으로 전달한다.
- 발견·함정·반응 등 GM 처리가 필요한 사건은 적 토큰이 없어도 전용 고정 GM 응답 계약으로 처리한다. 백엔드는 스키마와 현재 작업·차례를 검증하며 이동량·토큰 위치·턴 종료는 서버 소유자가 결정한다.
- 확인한 경로의 남은 칸은 자동 진행하지 않는다. 실제 위치·이미 사용한 이동량은 보존하고 GM 처리 결과가 확정된 뒤 같은 플레이어 차례의 다음 입력을 기다린다.
- 기존 CombatEncounter, CombatActionApplicationService, MapMovementCoordinator, Combat Map operation/recovery 경계를 확장한다. 새 aggregate, module, deployment unit, 메시지 broker는 추가하지 않는다.
- 지도 UI 이동과 /combat/actions 이동 제출 경로 모두 같은 사건 처리·멱등 복구 규칙으로 연결한다.

## 수용 기준

- 요구사항 추적: AC-05, AC-06, AC-07, AC-08, AC-16, BR-05, BR-06, BR-08, BR-16, BR-18, BR-19, EX-08, G-03, ST-UC-002-01, ST-UC-002-02, ST-UC-002-03, UC-002.
- 소유 경계: 전투 차례·이동량 정본(CombatEncounter), 전투 행동 처리 서비스(CombatActionApplicationService), 지도 이동 조정기(MapMovementCoordinator), 전투 지도 서비스(Combat Map); 시행 지점: 전투 지도 서비스가 실제 중단 칸·이동량을 모험 진행 서비스로 전달하고, 전용 GM 응답과 현재 작업·차례를 검증한 뒤 미완료 작업을 이어서 처리.
- UC-002, BR-05/08/16/18/19, AC-05/07/08/16/20, EX-08, ST-UC-002-01~03을 충족한다. CombatEncounter는 차례·이동 예산의 정본이고 Combat Map은 실제 토큰 위치·이동 결과의 정본이다.
- 사건이 중간 칸에서 발생하면 실제 통과 경로의 위치·이동량만 반영한다. 확인 전 지도는 바뀌지 않고, 모든 필수 판정이 끝나기 전 공개 상태를 변경하지 않는다.
- 전용 고정 GM 응답 계약은 판정 결과·공개 서술·필요한 판정/사용자 선택·전투 시작 여부를 표현한다. 적 토큰 ID에 사건을 묶지 않고, 서버가 부여한 작업·차례 식별자만 연결한다.
- 잘못되거나 작업·차례가 불일치하는 GM 응답은 확정 상태를 바꾸지 않는다. 같은 기존 작업으로 미완료 단계만 재개하며 위치·이동량·비용·GM 결과를 중복 적용하지 않는다.
- 사건 판정·서술이 끝나도 플레이어 차례를 유지한다. 새 경로 확인, 다른 허용 행동 또는 명시적 턴 종료만 다음 상태 변경을 확정한다. 남은 경로 자동 이동과 GM 서술 기반 토큰 이동·턴 종료는 없다.
- 금지 사례: 지도 확정 직후·이동 예산 저장 직후·작업 완료 기록 직전의 장애 복구에서 실제 거리의 이동량 차감을 누락하거나 두 번 차감하고, 위치·이동 예산·완료 결과가 서로 어긋나거나 GM 서술을 다시 적용한다. 경로 확인 전에 위치·안개를 바꾸거나 중단 지점 이후 이동량을 소비한다.

## 테스트 계약

- 개발용 진단 검증 (Architecture Spec §9.1·INV-01): 이 계획이 바꾸는 각 시작·완료·대체 경로·재시도·실패 분기에서 기존 `DevelopmentDiagnostics`(개발 진단 기능)와 `dev_` 개발 로그 형식을 확인한다. `ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED`(개발용 상세 진단 로그 설정)가 꺼졌을 때 상세 로그가 없고, 켜졌을 때 식별자·입력 크기/개수·기대/실제 값·선택 분기·소요 시간·실패 종류와 다음 복구 동작이 드러나며, 프롬프트·응답·인증 정보·불필요한 플레이어 입력·비공개 지도 정보를 기록하지 않고 실제 동작도 바꾸지 않는지 검사한다.

- 장애 복구 수용 조건: 지도 확정 직후, 이동 예산 저장 직후, 작업 완료 기록 직전의 세 경계에서 중단되어도 저장 상태를 다시 읽고 동일 서버 작업 식별자로 재시도하여 실제 도달 위치·실제 거리·이동 예산·완료 기록이 한 번 적용된 결과로 일치해야 한다. 지도 UI 이동과 `/combat/actions` 이동 제출 모두 같은 조건을 충족하며 이동량 차감 누락·이중 차감·서술 재적용을 허용하지 않는다.
- 구체적 장애 주입 검사: 두 진입점 각각에 대해 ① 지도 확정 직후 ② 이동 예산 저장 직후 ③ 작업 완료 기록 직전에 프로세스 중단을 주입한다. 각 경우 저장된 지도·전투·작업 상태에서 새 처리 인스턴스를 복원하고 같은 서버 작업 식별자로 재시도한다. 실제 도달 칸과 거리, 남은 이동 예산의 정확히 한 번 차감, 완료 기록 및 같은 플레이어 차례가 일치하고, 이미 확정한 서술이 반복되지 않는지 확인한다. (Architecture Spec §4.3, BR-08·BR-16·BR-19·AC-16)

- 단위/정책: 경로 유효성, 확인 전 읽기 전용, 칸별 안개·이동량, 중단 위치. 요구사항 식별자: AC-05, AC-06, AC-07, AC-08, AC-16, BR-05, BR-06, BR-08, BR-16, BR-18, BR-19, EX-08, G-03, ST-UC-002-01, ST-UC-002-02, ST-UC-002-03, UC-002. 소유 경계와 실행 진입점: 전투 차례·이동량 정본(CombatEncounter), 전투 행동 처리 서비스(CombatActionApplicationService), 지도 이동 조정기(MapMovementCoordinator), 전투 지도 서비스(Combat Map); 전투 지도 서비스가 실제 중단 칸·이동량을 모험 진행 서비스로 전달하고, 전용 GM 응답과 현재 작업·차례를 검증한 뒤 미완료 작업을 이어서 처리. 추적 수용 조건: 전투 지도는 상호작용 화면의 중앙에 배치하고, 선택한 캐릭터 토큰을 클릭·드래그해 경로를 정한다. 반례: 경로 확인 전에 위치·안개를 바꾸거나 중단 지점 이후 이동량을 소비한다. 검증 사례: 단위/정책: 경로 유효성, 확인 전 읽기 전용, 칸별 안개·이동량, 중단 위치. 추가 계약 검증: 사건에 적 토큰 ID가 없는 경우, 스키마 불일치·작업/차례 불일치 GM 응답, 응답 유실 후 동일 작업 재개에서 지도 위치·이동량·자원·서술의 중복 적용 없음, 지도 UI와 전투 행동 제출 두 진입점의 같은 정책 적용.
- `ui ~ entity` E2E: 해당 없음 — 경로 실행·중단 지점·이동량·전용 응답은 정책·계약·통합 검사로 검증한다. 실제 에이전트 포함 브라우저 E2E는 원본 자료·지도 준비·자연어 승인까지 포함해 모든 선행 구현 뒤 #375에서 한 번 실행한다.

## 관련 명세

- Product Spec: docs/specs/combat-movement-spell-range/product-spec.md
- Architecture Spec: docs/specs/combat-movement-spell-range/architecture-spec.md

## 다이어그램

- 제품: UC-002 미리보기와 실행 — ![UC-002 미리보기와 실행](https://github.com/omegafrog/dnd-master/blob/harness/spec-me/cff78917-af41-4451-87e5-df5ba5376d49/docs/specs/combat-movement-spell-range/diagrams/product/UC-002.activity.svg?raw=true)
- 아키텍처: 전투 이동 중단·같은 차례 재개 상태 — ![전투 이동 중단·같은 차례 재개 상태](https://github.com/omegafrog/dnd-master/blob/harness/spec-me/cff78917-af41-4451-87e5-df5ba5376d49/docs/specs/combat-movement-spell-range/diagrams/architecture/combat-movement-interruption.state.svg?raw=true)
