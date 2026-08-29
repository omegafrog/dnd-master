# Plan RN-001: Runtime Narrative State and Epistemic Boundaries

- Issue: #226
- Parent Issue: #225
- Status: `ready-for-agent`
- Dependencies: #218, #219
- Source: #206

## 구현 목적

세션 중 확정된 세계 상태와 Player/NPC별 지식을 구조화한다. NPC가 모르는 정보를 사용하거나 공개된 사실이 다시 숨겨지는 문제를 막고, 검증된 State Delta만 Runtime에 반영할 기반을 만든다.

## 구현 범위

- `NarrativeState` aggregate: WorldFact, RevealedFact, CharacterKnowledge, Belief, Relationship, ActiveThread, RecentEvent.
- actor-scoped `NarrativeContext` projection과 `factsKnownBy`, `characterKnows`, `canReveal` 계약.
- State Delta validation, optimistic version, monotonic reveal, belief/world-fact 분리.
- 기존 `AdventureContext.npcState`, runtime JSON, compaction과 legacy compatibility projection.
- adventure-service repository/migration/audit seam.

## 제외 범위

- Best-of-N, Verifier/rewrite, Exemplar retrieval.
- Character/Map/Dice 상태 직접 소유.
- 자유 transcript의 canonical 승격.

## Acceptance Criteria

- World Fact와 Player/NPC Knowledge가 독립 조회된다.
- NPC A만 아는 사실이 NPC B/Player context에 포함되지 않는다.
- Revealed Fact는 replanning/compaction 뒤에도 hidden으로 복귀하지 않는다.
- Belief는 World Fact를 변경하지 않는다.
- stale/invalid State Delta는 commit되지 않는다.
- 정적 RAG 결과가 Runtime State를 덮어쓰지 않는다.

## Test Contract

- Policy unit: reveal monotonicity, actor isolation, belief separation, delta validation, version conflict.
- Integration: snapshot persistence, legacy JSON projection, repository optimistic locking.
- UI ~ entity E2E: 기존 message/GM turn에서 비밀 fact가 player 응답에 노출되지 않고, 공개 fact는 재접속 후 유지.

## 구현 순서

1. domain types/policies.
2. repository와 additive migration.
3. runtime context projection/compaction compatibility.
4. application commit integration 및 E2E.
