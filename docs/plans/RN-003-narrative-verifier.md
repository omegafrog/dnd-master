# Plan RN-003: Narrative Verifier and Bounded Rewrite

- Issue: #228
- Parent Issue: #225
- Status: `completed`
- Dependencies: RN-001, RN-002
- Source: #207

## 구현 목적

Writer의 자연어가 계획·규칙·상태·정보 경계를 벗어나지 않는지 최종 게이트에서 검사한다. 오류 발생 시 같은 Resolved Turn의 의미를 보존한 rewrite만 한 번 허용한다.

## 구현 범위

- deterministic validator: secret leak, unsupported fact/state, rule mismatch, agency, NPC knowledge, turnplan deviation.
- `NarrativeVerificationContext`, `VerificationResult`, violation type/severity/evidence/instruction.
- semantic `NarrativeVerifierPort`와 `VerificationPolicy`.
- `RewritePort`, same-turn fingerprint, max one rewrite.
- 기존 `GmFinalValidator`, `TurnWriterPort`, `RuntimeTurnApplicationService` 통합.
- verification/rewrite/model metadata audit.

## 제외 범위

- Best-of-N plan selection 자체.
- TurnPlan/Runtime State schema 재설계.
- 무한 refinement, automatic prompt optimization.

## Acceptance Criteria

- SECRET_LEAK, RULE_MISMATCH, PLAYER_AGENCY_VIOLATION, NPC_KNOWLEDGE_VIOLATION, TURNPLAN_DEVIATION, STATE_CONTRADICTION 구조화 보고.
- ERROR 1개 이상이면 rewrite.
- WARNING만 있으면 반환 가능.
- rewrite는 계획·규칙 결과·State Delta·Story Stage를 변경하지 않음.
- rewrite 후 ERROR 잔존 시 추가 호출 없이 bounded failure.
- 검증 실패 응답은 state/conversation을 commit하지 않음.

## Test Contract

- Policy unit: violation classification, severity, rewrite count/fingerprint.
- Contract/integration: verifier/rewrite DTO, provider timeout, persisted audit, no duplicate saga.
- UI ~ entity E2E: secret leak/agency violation은 노출되지 않고, 정상 narration은 기존 UI에 표시.

## 구현 순서

1. violation/result/policy.
2. deterministic validator.
3. semantic verifier/rewrite adapter.
4. runtime presentation lifecycle와 E2E.
