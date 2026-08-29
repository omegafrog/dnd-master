# Plan 219: Bounded Writer Delivery

- Issue: #219
- Status: `ready-for-agent`
- Dependencies: #218

## 구현 목적

Planner와 Writer의 책임과 context를 분리한다. Writer는 최소 WriterContext와 persisted ResolvedTurnPlan만 받아 prose를 생성하며, 실패하면 Planner·rules·tools를 다시 실행하지 않고 Writer만 재시도한다.

## 구현 범위

- `TurnPlannerPort`, `TurnWriterPort`, `WriterContext`, `WriterProse` 추가.
- AI GM service의 Planner/Writer application service와 모델 adapter 분리.
- WriterContext whitelist: visible facts/style만 전달; future plan, hidden facts/motives, raw RAG, Planner reasoning 제외.
- Writer output을 prose-only type으로 제한.
- Writer 성공 시 prose, world transition, context, conversation을 원자적으로 commit.
- 기존 public `/messages`, `/gm-turns`, `/turns` response 호환 유지.

## 제외 범위

- Critic/Verifier 또는 prose fact extraction.
- Writer local detail의 canonical fact 자동 승격.

## Acceptance Criteria

- Planner는 prose를 만들지 않고 Writer는 decision/state/tool contract를 만들지 않는다.
- Writer가 실패하면 persisted ResolvedTurnPlan으로 Writer만 retry한다.
- 같은 ResolvedTurnPlan에서 Writer model/prompt가 달라도 canonical transition은 같다.
- Writer request payload에 prohibited hidden/future/raw data가 없다.

## Test Contract

- Policy unit: WriterContext whitelist, prohibited-field absence, WriterProse state/tool 불표현, retry no-replan.
- AI GM contract: Planner payload와 Writer payload가 분리되고 Writer prompt가 hidden input을 받지 않는다.
- Integration: Writer timeout/invalid prose 후 same resolved artifact retry; tool saga/state/conversation 중복 없음.
- UI ~ entity E2E: 기존 UI message → presentation success → 기존 GM response/narration 표시. 실패 후 retry도 단일 turn/state transition 유지.

## 구현 순서

1. Planner/Writer ports·DTO.
2. AI GM application/adapters.
3. Runtime orchestration과 atomic presentation commit.
4. endpoint compatibility 및 E2E.
