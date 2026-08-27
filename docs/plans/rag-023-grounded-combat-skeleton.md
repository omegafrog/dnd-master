# RAG-023: 근거 기반 Combat Skeleton 모험 계획

- 상태: `completed`
- 의존성: RAG-018 (completed)
- Product Spec: UC-COMBAT-001, BR-COMBAT-001~006, AC-COMBAT-001~005, AC-EVIDENCE-001
- Architecture Spec: Sections 3.3~3.7, 4.6, 5.5, 5.8~5.9, 11.1, 11.3
- GitHub Issue: [#198](https://github.com/omegafrog/dnd-master/issues/198)
- Parent Issue: [#194](https://github.com/omegafrog/dnd-master/issues/194)

## 구현 목적

계획 본문에 전투·적·보스가 등장하면서 구조화된 실행 정보가 비어 있는 계획을 READY로 만들지 않는다. 전투 참가자와 Source Fact는 공개 STORYBOOK/RULEBOOK 근거에 묶고, 목적·시작·성공·실패 또는 fail-forward 결과는 근거에 없는 설정 사실을 발명하지 않는 GM Scaffolding으로 구분한다.

## 사용자·엔티티 흐름

```text
새 모험 계획 생성
→ 단계별 Combat Requirement 분류
→ Combat Skeleton + SourceFactClaim 후보
→ field-specific evidence와 graph 검증
→ tactical requirement intent 저장
→ 완전한 계획만 READY·불완전 계획은 BLOCKED
```

## 구현 범위

- `CombatRequirement`, `CombatSkeleton`, `CombatParticipant`, `SourceFactClaim`
- objective, trigger, participants, success, failure/fail-forward, rewards 계약
- 필드별 citation key와 단계 evidence 소속 검증
- 전투 암시와 REQUIRED/POSSIBLE/NONE 분류의 일관성
- future mapped combat stage의 `TacticalPreparationRequirement.REQUIRED`
- story-plan projection v2와 AI plan candidate schema
- stage JSON/history의 additive persistence와 legacy reader
- 이미 시작된 legacy plan은 읽기 호환, 시작 전 legacy plan은 READY 전 재생성
- player projection에서 숨김 전투·결말 정보 비공개 유지

## 수용 기준

- 필수 전투 단계는 참가자, 목적, 시작, 성공, 실패/fail-forward가 모두 있어야 READY가 된다.
- 적·보스·고유 보상·수량은 해당 필드에 연결된 단계 근거가 없으면 차단된다.
- 일반 룰북 장 설명만으로 특정 STORYBOOK 전투 사실을 지지하지 않는다.
- 비전투 단계는 명시적인 NONE을 가지며 빈 참가자가 허용된다.
- 미래 전투는 구체 좌표 없이 전술 준비 필요 의도만 저장한다.

## 테스트 계약

- 정책 단위 테스트: combat completeness, requirement classification, source fact, scaffolding 제한
- projection 계약 테스트: v2 required fields, invalid candidate, legacy reader
- persistence 통합 테스트: stage JSON/history와 불변 revision round-trip
- UI ↔ Entity E2E: Potent Brew 계획 생성 → 쥐·최종 거미 단계 Combat Skeleton/근거 확인 → READY gate
- player API E2E: 현재 공개 정보만 노출되고 숨김 참가자·결말은 비공개

## 제외 범위

- 미래 전술 좌표·토큰·시야 생성
- dependency-aware repair closure
- 현재 단계 tactical job 실행

## 완료 증거

- Potent Brew rat/spider stage projection fixture
- READY/BLOCKED 검증 결과
- legacy compatibility와 player-safe projection 증거
