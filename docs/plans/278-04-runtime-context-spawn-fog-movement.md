# Plan 278-04 — Runtime Context Spawn, Fog Trigger + Map Movement Repair

- Issue: [#294](https://github.com/omegafrog/dnd-master/issues/294)
- Parent: [#292](https://github.com/omegafrog/dnd-master/issues/292)
- Status: Planned
- Dependencies: [#290](https://github.com/omegafrog/dnd-master/issues/290), [#291](https://github.com/omegafrog/dnd-master/issues/291) — both completed in the tracker

## 한국어 구현 목적

브라우저 검증에서 확인된 런타임 결함을 기존 Combat Map vertical slice 안에서 보완한다. 모험 시작 위치는 맵 파일의 좌표가 아니라 현재 턴·시추에이션·진입 맥락으로 결정되어야 하며, 이동과 명시적 tactical trigger 뒤에는 authoritative visibility와 player-safe projection이 같은 런타임 상태를 반영해야 한다. Web UI의 유효한 이동 요청은 Adventure Runtime을 거쳐 Combat Map에 성공적으로 commit되어야 한다.

## 구현 범위

- Adventure Runtime의 현재 시추에이션/턴/진입 맥락을 `MapActivationContext`로 전달
- prepare 단계의 하드코딩 `(0,0)` PLAYER 생성 제거 및 explicit/context/fallback spawn source 보존
- Web UI → `/turns` → durable runtime command → Combat Map move 경로의 실패 수정
- 이동 후 `VisibilitySnapshot` current/explored 갱신과 player projection 정렬
- `FOG_REVEAL` 등 planned tactical trigger의 tool/internal gateway 적용 및 visibility 재계산
- fresh database에서 `adventure_active_tactical_map` read model migration 보장

## 구현 순서

1. 현재 start/activation DTO와 runtime movement 경계를 재현 가능한 테스트로 고정한다.
2. 시추에이션/턴 기반 activation context를 전달하고 prepare의 명시 `(0,0)` 경로를 제거한다.
3. 이동 command의 serialization, expected version, cross-context 오류 매핑을 수정한다.
4. 이동·문·tactical trigger 후 snapshot과 player-safe projection을 갱신하고 trigger 도구 계약을 연결한다.
5. fresh-schema migration과 단위/통합/contract 회귀 테스트를 추가한다.

## 수용 기준

- 현재 Adventure context로 시작한 맵은 임의의 `(0,0)` 명시 토큰으로 고정되지 않고, 유효한 context candidate 또는 정책상 deterministic fallback으로 배치된다.
- spawn source와 계산 입력이 테스트 또는 구조화된 진단에서 구분된다.
- 브라우저의 유효한 인접 이동이 2xx로 완료되고 reload 후 token 위치와 map version이 유지된다.
- 이동 후 authoritative current/explored가 갱신되고 player projection은 허용된 범위만 노출한다.
- `FOG_REVEAL` trigger/tool 호출은 대상 fog를 해제하고 같은 command transaction에서 snapshot/player view를 갱신한다.
- stale version, invalid path, hidden token 정보 누출은 기존 guard/fail-closed 계약을 유지한다.
- 깨끗한 DB에서 모험 생성→시작→맵 표시가 수동 SQL 보정 없이 동작한다.

## 검증 계약

- Spawn context/policy unit 및 Adventure→Combat Map adapter contract test
- movement gateway/controller/runtime-command integration regression test
- fog trigger application/projection unit test
- fresh-schema migration/contract test
- `ui ~ entity` focused browser flow: 새 모험 시작 → PLAYER 위치 확인 → 인접 이동 → map version/위치/current-explored 변화 확인 → trigger 후 fog 변화 확인

## 관련 명세

- [Product Spec](../specs/278/product-spec.md) G-5~G-7, UC-4~UC-6, BR-6~BR-11, AC-6~AC-9
- [Architecture Spec](../specs/278/architecture-spec.md) activation context, spawn resolution, `VisibilitySnapshot`, tactical trigger, movement flow

## 구현 제약

- Combat Map bounded context 소유권을 유지한다.
- 자연어를 Combat Map이 해석하지 않으며, Adventure Runtime/AI가 후보를 제안하고 Combat Map이 검증한다.
- dynamic visibility를 static `INITIAL_FOG` 데이터와 혼동하지 않는다.
- 이 slice 범위를 넘는 전투 lifecycle 변경은 하지 않는다.
