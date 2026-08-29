# Product Spec: 신뢰 가능한 GM Turn lifecycle과 평가 기반 생성 품질 개선

## 1. Problem and Context

현재 GM 생성 흐름은 계획 결정, 규칙·도구 해결, 자연어 작성, 공개 응답이 하나의 진행처럼 취급될 위험이 있다. Writer 실패나 재시도가 이미 확정된 세계 상태·대화·도구 결과를 다시 실행하면 중복 실행과 상태 불일치가 발생한다. 기존 저장 형식과 재생 요청도 새 lifecycle과 공존해야 한다.

동시에 GM 품질 개선은 수동 prompt 수정에 의존하면 특정 장면 최적화, 회귀 누락, 역할 간 책임 혼선을 낳는다. Fine-tuning은 충분한 평가·데이터가 확보된 뒤에도 구조적 문제가 남을 때만 검토해야 한다.

이 스펙은 열린 #218–#220을 핵심 범위로, #210을 후속 범위로, #211을 조건부 후속 범위로 구체화한다. 종료된 #203–#209와 #213–#217은 선행 결정이다.

핵심 흐름:

```text
Player Action → Plan → rule/tool resolution → persist Resolved Turn
→ bounded Writer delivery → verify/retry presentation → commit/return response
```

품질 개선 흐름:

```text
versioned Prompt Candidate → fixed Eval Dataset → hard gate
→ role-specific comparison → holdout check → human review → approve/rollback
```

## 2. Goals and Desired Outcomes

- G-001: 계획·해결·표현을 구분해 Writer 실패가 게임 상태나 도구 실행을 중복 변경하지 않게 한다.
- G-002: Resolved Turn을 prose 없이 재현 가능하게 저장하고 presentation 상태를 별도 추적한다.
- G-003: Writer에 필요한 최소 공개 가능 context만 전달하고 hidden/future 정보와 planner reasoning을 차단한다.
- G-004: Writer 실패는 Writer 단계만 제한 횟수로 재시도한다.
- G-005: 기존 public turn API, legacy 저장 자료, retry/replay를 보존한다.
- G-006: 인증된 read-only diagnostics로 Planner/Resolved/Writer artifact를 추적한다.
- G-007: prompt와 생성 설정을 versioned artifact로 관리하고 Eval 결과로 비교한다.
- G-008: 역할별 prompt를 독립 최적화하고 hard constraint 회귀를 품질 점수로 상쇄하지 않는다.
- G-009: train/dev/holdout을 분리해 과적합을 줄이고 승인·rollback을 재현 가능하게 한다.
- G-010: fine-tuning은 선행 계층으로 해결되지 않은 반복 품질 문제에 한해 증거 기반으로 검토한다.

## 3. Users and Actors

- **Solo Player**: 행동 입력, 공개 응답 수신. 내부 artifact·hidden 정보·reasoning 비공개.
- **AI Planner**: 허용된 상태에서 TurnPlan 제안.
- **Rule/Tool Resolver**: 계획의 판정과 외부 상태 변경 결과 확정.
- **GM Writer**: Resolved Turn과 최소 WriterContext를 prose로 표현.
- **Narrative Verifier**: 규칙·연속성·비밀·agency 위반 검사.
- **Runtime Authority**: resolved 결과, 공개 결과의 순서·중복·commit 통제.
- **Developer/Operator**: diagnostics, Eval report, prompt/model registry 조회·승인.
- **Prompt Optimizer**: 역할별 prompt candidate 생성·비교.
- **Evaluator/Reviewer**: metric, 대표 출력, holdout 결과 검토.
- **Model Provider**: 생성·평가·tuning 결과 제공.

## 4. Ubiquitous Language and Terminology

- **TurnPlan**: 턴 의도, 공개 내용, 예상 변화와 필요한 해결을 구조화한 계획.
- **ResolvedTurnPlan**: TurnPlan에 규칙·도구 결과와 확정 State Delta가 결합된 prose 이전의 불변 결과.
- **Runtime Turn**: 하나의 GM Turn lifecycle 기록.
- **Presentation**: Resolved Turn을 플레이어 전달용 prose와 공개 결과로 변환하는 단계.
- **WriterContext**: Writer가 받을 최소 공개 가능 상태·장면·Resolved Turn·표현 지침.
- **Planner/Writer Artifact**: 각 단계 입력·출력·버전·fingerprint·검증 결과의 내부 기록.
- **Diagnostics**: lifecycle artifact와 호환성 결과를 read-only로 확인하는 기능.
- **Legacy Projection**: 기존 RuntimePlan/JSON을 새 lifecycle 표현으로 읽는 호환 모델.
- **Prompt Candidate**: role, prompt/config, model, parent version을 가진 비교 대상.
- **Optimization Run**: dataset, candidate, model, metric, 선택·거부 사유의 재현 단위.
- **Hard Metric**: rule violation, secret leak, agency violation, schema failure 등 탈락 기준 지표.
- **Soft Metric**: coherence, pacing, NPC consistency, interestingness, prose quality 등 품질 지표.
- **Holdout/Regression Set**: 최적화·튜닝에 사용하지 않는 최종 검증 집합.
- **Tuning Gate**: 선행 개선 후에도 구조적 문제가 남는지 판단하는 fine-tuning 도입 조건.

## 5. Core Use Cases

### UC-218-1: Resolved Turn 저장

1. Runtime Authority가 TurnPlan과 rule/tool 결과를 확정한다.
2. ResolvedTurnPlan을 prose 없이 저장한다.
3. Runtime Turn을 미표시 resolved 상태로 기록한다.
4. presentation 전에는 adventure state, conversation, player-visible response를 commit하지 않는다.
5. 같은 요청 재시도는 저장된 resolved 결과를 재사용한다.

### UC-218-2: 해결 결과 재생

1. resolved artifact 또는 legacy projection을 식별한다.
2. idempotency와 turn fingerprint를 비교한다.
3. 이미 해결된 결과가 있으면 rule/tool을 재실행하지 않는다.
4. 미표시 결과면 presentation만 수행한다.

### UC-219-1: 제한된 Writer 전달

1. 선택된 ResolvedTurnPlan과 WriterContext를 준비한다.
2. hidden/future/raw RAG/planner reasoning이 없는지 확인한다.
3. Writer는 prose만 생성한다.
4. State Delta나 tool call이 있으면 거부한다.
5. 실패하면 Writer 단계만 제한 횟수로 재시도한다.
6. 성공한 prose를 검증하고 기존 public turn API 형태로 반환한다.

### UC-220-1: Lifecycle Diagnostics 조회

1. 인증된 개발자가 session/turn으로 diagnostics를 조회한다.
2. Planner, Resolved, Writer artifact와 상태·fingerprint를 확인한다.
3. prompt/model/dataset, retry, verifier, commit 결과를 read-only로 확인한다.
4. 조회는 게임 상태와 artifact를 변경하지 않는다.

### UC-220-2: Legacy 호환·회귀 검증

1. legacy RuntimePlan과 JSON을 읽는다.
2. 새 lifecycle 조회·재생 표현으로 변환한다.
3. presented/resolved retry/replay와 중복 요청을 검증한다.
4. 변환 불가는 추측·자동 재생성 없이 식별 가능한 오류로 반환한다.

### UC-210-1: Prompt 후보 평가·선정

1. production baseline을 versioned prompt artifact로 등록한다.
2. 역할별 candidate를 생성한다.
3. 고정 train/dev Eval Dataset에서 실행한다.
4. hard metric을 먼저 검사하고 회귀 후보를 탈락시킨다.
5. 유효 후보를 soft metric으로 비교한다.
6. holdout/regression과 대표 출력 검토를 통과한 후보만 승인한다.
7. registry에 선택·거부·rollback 정보를 남긴다.

### UC-211-1: Fine-tuning 도입 판단

1. stable contract, Eval, baseline, optimized prompt, curated data, failure taxonomy, holdout 준비 여부를 확인한다.
2. 선행 계층으로 해결되지 않은 반복 failure category를 문서화한다.
3. 필요한 역할만 tuning 후보로 선택한다.
4. provenance·quality gate와 train/dev/holdout을 적용한다.
5. base model + optimized prompt와 tuned model을 동일 조건으로 비교한다.
6. hard metric 비악화, 목표 soft metric 개선, holdout 유지, 비용 허용을 확인한다.
7. 실패 시 optimized prompt/base model을 유지하고, 성공 시 역할 단위 배포·rollback한다.

## 6. Business Rules and Invariants

- BR-001: ResolvedTurnPlan은 prose와 독립적으로 저장 가능하다.
- BR-002: presentation 전에는 world state, conversation, tool effect를 commit하지 않는다.
- BR-003: rule/tool 해결은 idempotency key와 turn fingerprint로 중복 실행을 막는다.
- BR-004: Writer 재시도는 Writer에만 적용한다.
- BR-005: Writer는 WriterContext와 ResolvedTurnPlan만 사용한다.
- BR-006: Writer 결과는 prose만 포함한다.
- BR-007: public API 계약은 lifecycle 내부 분리와 무관하게 유지한다.
- BR-008: legacy 자료는 읽고 재생하되 묵시적 상태 변경을 일으키지 않는다.
- BR-009: diagnostics는 인증된 read-only 접근만 허용한다.
- BR-010: prompt candidate는 role/version/model/dataset/run을 추적한다.
- BR-011: 역할별 prompt/model은 독립 관리한다.
- BR-012: hard metric 악화 후보는 soft metric 상승만으로 선택할 수 없다.
- BR-013: optimization/tuning은 holdout을 사용하지 않는다.
- BR-014: 학습 데이터는 provenance·quality gate를 가지며 unsafe/미해결 오류 자료를 제외한다.
- BR-015: fine-tuning은 선행 조건 미충족 시 시작하지 않는다.
- BR-016: production 후보는 baseline과 비교·재현 가능해야 한다.
- BR-017: tuning 결과는 역할 단위로 채택·rollback한다.

## 7. States and State Transitions

### Runtime Turn

```text
PLANNING → RESOLVED → PRESENTING → PRESENTED
                         └──────→ PRESENTATION_FAILED
PLANNING ────────────────→ FAILED
```

- `PLANNING`: TurnPlan 생성·선택 중.
- `RESOLVED`: 규칙·도구 결과와 State Delta 확정. 아직 표시·commit하지 않음.
- `PRESENTING`: Writer/Verifier 처리 중.
- `PRESENTED`: 공개 응답과 필요한 commit 완료.
- `PRESENTATION_FAILED`: bounded retry 소진. resolved artifact 보존.
- `FAILED`: planning/resolution 실패로 resolved 결과 없음.

재시도는 `RESOLVED → PRESENTING`에 한정한다. `PRESENTED` replay는 저장 결과를 반환하며 tool을 재실행하지 않는다.

### Prompt Candidate

```text
DRAFT → EVALUATED → REJECTED
                  └→ PENDING_REVIEW → APPROVED → ACTIVE
                                           └────→ ROLLED_BACK
```

### Fine-tuning Proposal

```text
NOT_JUSTIFIED → GATED → TRAINED → EVALUATED → REJECTED
                                          └→ APPROVED → ACTIVE → ROLLED_BACK
```

## 8. Failures, Exceptions, and Boundary Conditions

- planner schema/candidate 오류: resolved로 진행하지 않고 원인·artifact를 남긴다.
- rule/tool resolution 실패: 부분 재실행 없이 기존 idempotency/saga 경계를 따른다.
- 동일 fingerprint 중복 요청: 기존 결과 반환. 충돌 fingerprint: 명시적 conflict.
- Writer context 경계 위반: 호출 거부, 차단 항목 기록.
- prose 외 Writer 결과: 실패 처리 후 Writer만 bounded retry.
- Writer timeout/error: retry 소진 후 안전한 presentation failure.
- Verifier ERROR: 허용 횟수 내 Writer만 rewrite, 초과 시 공개하지 않음.
- legacy 변환 실패: 추측·자동 재생성 없이 호환성 오류.
- diagnostics 권한 없음: 인증 오류.
- prompt hard metric 회귀: soft metric과 무관하게 탈락.
- dataset leakage: run 무효화.
- tuning 데이터 품질·권한·provenance 불명확: 학습 제외.
- tuned model 비용·지연 초과: 채택하지 않거나 rollback.

## 9. Inputs and Outputs

### Runtime

입력: player action/map interaction, session/turn/idempotency, TurnPlan, rule/tool 결과, Runtime Narrative State, 공개 정책, prompt/model metadata.

출력: Planner/Resolved/Writer artifact, Runtime Turn 상태·retry, commit/presentation 결과, 기존 public API 응답, diagnostics projection.

### Optimization

입력: baseline/candidate prompt·config, model, versioned train/dev/holdout, Eval/rubric, parameters·seed.

출력: candidate별 hard/soft metric, baseline delta, regression report, review 기록, registry entry.

### Tuning

입력: optimized prompt baseline, provenance data/split, training method/hyperparameters.

출력: base/tuned metadata, 동일 Eval 결과, 비용·지연, 채택·rollback 결정.

## 10. Scope and Non-goals

### In scope

- #218–#220 lifecycle, bounded writer, diagnostics, legacy compatibility.
- #210 offline role-specific prompt optimization과 registry.
- #211 도입 gate, 데이터 품질·분할, baseline 비교, 역할별 tuning/rollback 판단.
- 선행 #203–#209, #213–#217 계약의 연결.

### Non-goals

- TurnPlan, Runtime Narrative State, Verifier, Best-of-N, Style Exemplar 자체 재설계.
- 새로운 게임 규칙·시나리오 컴파일·VTT 기능.
- online RL 또는 사용자별 실시간 weight update.
- 무검토 production prompt/model 자동 배포.
- fine-tuning을 prompt optimization보다 먼저 적용.
- Eval Suite 최초 metric/contract 재설계.

## 11. Priorities and Trade-offs

1. **P0** #218 lifecycle safety.
2. **P0** #219 writer isolation.
3. **P0** #220 observability/compatibility.
4. **P1** #210 prompt optimization.
5. **P2** #211 model tuning.

상태 보존·hard safety를 creative quality·비용보다 우선한다. 자동화 편의보다 재현성·감사 가능성·rollback을 우선한다.

## 12. Success Conditions and Acceptance Criteria

- AC-001: resolved turn은 prose 없이 저장·조회·재생된다.
- AC-002: Writer 실패·재시도 시 world state, conversation, tool effect가 중복 변경되지 않는다.
- AC-003: Writer에 hidden/future/raw RAG/planner reasoning이 전달되지 않는다.
- AC-004: Writer의 state delta/tool call은 거부되고 bounded retry된다.
- AC-005: 기존 public turn API 응답이 유지된다.
- AC-006: 인증된 read-only diagnostics에서 전체 lifecycle과 retry/replay를 확인한다.
- AC-007: legacy row/JSON이 새 조회·재생 흐름에서 회귀 없이 처리된다.
- AC-008: baseline과 role별 candidate를 동일 Eval Suite에서 비교한다.
- AC-009: hard metric 회귀 후보는 soft score가 높아도 선택되지 않는다.
- AC-010: run의 dataset/model/prompt/candidate/metric/선택 사유를 재현한다.
- AC-011: holdout/regression에서 최종 prompt를 별도 검증한다.
- AC-012: production prompt는 승인·rollback 가능하다.
- AC-013: fine-tuning 전 선행 조건과 미해결 failure category가 기록된다.
- AC-014: tuning data provenance, quality gate, split을 검증한다.
- AC-015: tuned model은 base model + optimized prompt와 동일 조건으로 비교한다.
- AC-016: hard constraint 비악화·holdout 개선·운영 비용 허용을 모두 만족하지 못하면 tuning하지 않는다.
- AC-017: 역할별 tuning이 다른 역할의 model configuration을 강제하지 않는다.
