# Product Spec: 실패 원인 보존과 복구 가능한 실행 파이프라인

## 1. Problem and Context

Scenario Package 컴파일과 GM Turn 처리에서 서로 다른 실패 원인이 너무 일찍 하나의 상태나 오류로 평탄화된다. 그 결과 어떤 후보가 왜 불완전했는지, 실패가 복구 가능한지, 재시도가 유효한지 판단하기 어렵다. 동일 입력의 결정론적 실패가 반복 재시도되고, 운영자는 원인을 추적하지 못하며, Solo Player는 실제 진행이 없는 GM 응답이나 설명 없는 tactical activation 실패를 경험할 수 있다. 진행률도 실제 작업 상태와 무관한 값으로 표시될 수 있다.

## 2. Goals and Desired Outcomes

- **G-001**: 개별 Compilation Candidate의 완전성, 실패 이유, 복구 가능성을 전체 Compilation Outcome과 분리한다.
- **G-002**: 복구 가능한 candidate만 제한적으로 repair하고 동일 입력의 무의미한 반복을 방지한다.
- **G-003**: 내부 운영 진단을 보존하면서 Solo Player에게는 안정적인 외부 오류 계약을 제공한다.
- **G-004**: 성공한 GM Turn이 플레이어 의도를 반영하고 의미 있는 진행을 만든다는 조건을 보장한다.
- **G-005**: Tactical Scene의 lazy preparation 계약을 상태와 오류 응답으로 명확히 한다.
- **G-006**: 준비·컴파일 진행률을 실제 phase와 처리량에 맞춰 표시한다.
- **G-007**: GM Turn의 planning, rule/tool resolution, presentation을 분리해 Writer 실패가 상태 변경이나 도구 실행을 반복하지 않게 한다.
- **G-008**: prose 이전의 Resolved Turn을 저장·재생하고 presentation 상태를 별도 추적한다.
- **G-009**: Writer에는 최소 공개 가능 context만 전달하고 hidden/future 정보와 상태 변경 권한을 차단한다.
- **G-010**: 기존 public turn API와 legacy runtime 자료의 조회·재생 호환성을 유지한다.
- **G-011**: lifecycle artifact, fingerprint, retry, verifier, commit 결과를 인증된 read-only diagnostics로 추적한다.
- **G-012**: prompt와 생성 설정을 역할별 versioned artifact로 관리하고 고정 Eval Dataset으로 비교한다.
- **G-013**: hard constraint 회귀를 soft quality 향상으로 상쇄하지 않는다.
- **G-014**: train/dev/holdout 분리, human review, approval, rollback으로 prompt 변경을 재현 가능하게 한다.
- **G-015**: fine-tuning은 prompt·contract·Eval 개선 후에도 반복되는 증거 기반 failure에만 검토한다.
- **G-016**: tuning 결과를 역할별로 채택·rollback하고 base model + optimized prompt와 동일 조건으로 비교한다.

## 3. Users and Actors

| Actor | Need |
| --- | --- |
| Solo Player | 현재 준비 상태, 경고, 재시도 가능 여부를 이해하고 유효한 GM Turn 결과를 받는다. |
| Operator/Developer | candidate와 GM 처리 실패의 단계, 원인, 복구 가능성, 연관 요청을 추적한다. |
| Scenario Compiler | candidate를 검증·repair하고 package 수준 결과를 판정한다. |
| AI Game Master | 제한된 근거에서 candidate와 GM Turn 제안을 생성한다. |
| Adventure Runtime | GM Turn과 Tactical Scene 상태 계약을 집행한다. |
| Rule/Tool Resolver | TurnPlan의 판정과 외부 상태 변경 결과를 멱등하게 확정한다. |
| GM Writer | Resolved Turn을 플레이어 공개 prose로 표현한다. |
| Narrative Verifier | intent, 진행성, 규칙, 연속성, 비밀, agency 위반을 검사한다. |
| Prompt Optimizer | 역할별 prompt candidate를 생성하고 고정 Eval에서 비교한다. |
| Evaluator/Reviewer | hard/soft metric, holdout, 대표 출력을 검토하고 승인 여부를 결정한다. |

## 4. Ubiquitous Language and Terminology

- **Compilation Candidate**: 컴파일 중 개별적으로 검증되는 추출 후보.
- **Candidate Completeness**: `COMPLETE`, `PARTIAL`, `INVALID` 중 하나인 후보 수준 상태.
- **Candidate Recoverability**: `REPAIRABLE`, `MAYBE_REPAIRABLE`, `NON_REPAIRABLE` 중 하나인 복구 가능성.
- **Compilation Outcome**: `COMPLETE`, `COMPLETE_WITH_WARNINGS`, `FAILED` 중 하나인 package 수준 결과.
- **Meaningful Progress**: GM Turn이 세계 상태, 정보, 결과, 결정 또는 진행을 실질적으로 전진시킨 상태.
- **Tactical Readiness**: 현재 stage의 Tactical Scene이 activation 가능한지 나타내는 상태. StoryPlan readiness와 별개다.
- **TurnPlan**: 플레이어 의도, 필요한 판정·도구, 예상 변화를 구조화한 GM Turn 계획.
- **ResolvedTurnPlan**: TurnPlan에 rule/tool 결과와 확정 State Delta가 결합된 prose 이전의 불변 결과.
- **Presentation**: ResolvedTurnPlan을 플레이어 공개 prose와 응답으로 변환·검증하는 단계.
- **WriterContext**: GM Writer에 전달하는 최소 공개 가능 상태, 장면, Resolved Turn, 표현 지침.
- **Lifecycle Artifact**: Planner, Resolved, Writer 단계별 입력·출력·버전·fingerprint·검증 결과의 내부 기록.
- **Legacy Projection**: 기존 RuntimePlan/JSON을 새 lifecycle 조회·재생 계약으로 읽는 호환 표현.
- **Prompt Candidate**: role, prompt/config, model, parent version을 가진 평가 대상.
- **Hard Metric**: rule violation, secret leak, agency violation, schema failure처럼 후보를 즉시 탈락시키는 지표.
- **Soft Metric**: coherence, pacing, NPC consistency, interestingness, prose quality를 비교하는 지표.
- **Holdout/Regression Set**: 최적화·튜닝에 사용하지 않는 최종 검증 자료.

## 5. Core Use Cases

### UC-001 Candidate 진단과 컴파일 결과 판정

1. Scenario Compiler가 모든 candidate를 추출하고 검증한다.
2. 각 candidate에 completeness, validation code/message, recoverability를 부여한다.
3. repair 가능한 불완전 candidate는 최대 1회 repair한다.
4. 최종 candidate 상태와 required/optional 정책으로 Compilation Outcome을 판정한다.
5. 운영자가 candidate별 원인과 repair 이력을 조회할 수 있다.

### UC-002 GM Turn 실패 진단과 외부 응답

1. Adventure Runtime이 GM Turn의 처리 단계별 결과를 추적한다.
2. 실패 시 내부 failure code, stage, retryability, root cause, correlation 정보를 보존한다.
3. Solo Player에게는 안정적인 일반 오류와 재시도 가능 여부만 제공한다.
4. transient provider 실패만 최대 1회 자동 재시도한다.

### UC-003 GM Turn 의미 진행 검증

1. Solo Player가 행동 의도를 입력한다.
2. AI Game Master가 plan, resolution, narration 후보를 생성한다.
3. Adventure Runtime이 의도 반영, 행동 해결, 의미 있는 진행, narration 일치를 검증한다.
4. 조건을 충족한 결과만 성공 GM Turn으로 확정한다.

### UC-004 Tactical Scene lazy preparation

1. Adventure Runtime이 새 stage에 진입한다.
2. 해당 stage의 Tactical Scene 준비가 비동기로 시작된다.
3. `READY` 전 activation 요청은 현재 readiness를 포함한 구체적 실패를 반환한다.
4. 클라이언트는 readiness를 확인하고 `READY` 후 activation을 다시 요청한다.

### UC-005 실제 진행률 표시

1. 처리 시스템이 현재 phase와 완료/전체 처리량을 제공한다.
2. 전체 처리량을 알 수 있으면 UI가 실제 비율을 표시한다.
3. 전체 처리량을 알 수 없으면 phase와 indeterminate 상태를 표시한다.

### UC-006 Resolved Turn 저장과 재생

1. Adventure Runtime이 TurnPlan과 rule/tool 결과를 확정한다.
2. ResolvedTurnPlan을 prose 없이 저장한다.
3. presentation 전에는 world state, conversation, player-visible response를 commit하지 않는다.
4. 같은 idempotency key와 fingerprint의 재요청은 저장된 resolved 또는 presented 결과를 재사용한다.
5. rule/tool operation을 다시 실행하지 않고 필요한 presentation만 재개한다.

### UC-007 제한된 Writer presentation

1. Adventure Runtime이 ResolvedTurnPlan과 최소 WriterContext를 준비한다.
2. hidden/future/raw RAG/planner reasoning이 없는지 검증한다.
3. Writer는 prose만 생성한다.
4. state delta나 tool call이 포함되면 거부한다.
5. presentation 결과를 Narrative Verifier가 검사한다.
6. 성공 결과만 commit하고 기존 public turn API로 반환한다.

### UC-008 Lifecycle diagnostics와 legacy 호환

1. 인증된 운영자/개발자가 session/turn으로 diagnostics를 조회한다.
2. Planner, Resolved, Writer artifact와 failure stage, fingerprint, retry, verifier, commit 결과를 확인한다.
3. legacy RuntimePlan/JSON은 Legacy Projection으로 읽고 재생한다.
4. 변환 불가는 추측이나 자동 재생성 없이 식별 가능한 호환성 오류로 반환한다.

### UC-009 Prompt 후보 평가·승인

1. production baseline을 versioned prompt artifact로 등록한다.
2. 역할별 Prompt Candidate를 생성한다.
3. 고정 train/dev Eval Dataset에서 hard metric을 먼저 검사한다.
4. 통과 후보만 soft metric으로 비교한다.
5. holdout/regression과 대표 출력 검토를 통과한 후보만 승인한다.
6. 선택·거부·rollback 정보와 실행 조건을 보존한다.

### UC-010 Fine-tuning 도입 판단

1. stable contract, Eval, optimized prompt, curated data, failure taxonomy, holdout 준비 여부를 확인한다.
2. 선행 개선으로 해결되지 않은 반복 failure category를 문서화한다.
3. 필요한 역할만 tuning 후보로 선택한다.
4. provenance·quality gate와 train/dev/holdout을 적용한다.
5. base model + optimized prompt와 tuned model을 동일 조건으로 비교한다.
6. hard metric 비악화, 목표 soft metric 개선, holdout 유지, 비용 허용을 모두 만족한 경우만 역할별로 채택한다.

## 6. Business Rules and Invariants

- **BR-001**: Candidate Completeness와 Compilation Outcome은 별도 상태 모델이다.
- **BR-002**: required candidate가 최종 `PARTIAL` 또는 `INVALID`면 Compilation Outcome은 `FAILED`다.
- **BR-003**: optional candidate만 최종 `PARTIAL` 또는 `INVALID`면 Compilation Outcome은 `COMPLETE_WITH_WARNINGS`다.
- **BR-004**: 모든 candidate가 `COMPLETE`면 Compilation Outcome은 `COMPLETE`다.
- **BR-005**: repair는 candidate당 최대 1회다.
- **BR-006**: 동일 입력과 동일 정책에 대한 package 수준 자동 재시도는 하지 않는다.
- **BR-007**: 상세 candidate·GM failure 진단은 운영자/개발자 전용이다.
- **BR-008**: Solo Player에게 내부 root cause나 원본 provider 응답을 노출하지 않는다.
- **BR-009**: provider timeout/unavailable만 최대 1회 자동 재시도한다.
- **BR-010**: JSON, citation, judgment, narration 검증 실패는 자동 재시도하지 않는다.
- **BR-011**: version conflict는 최신 상태 재조회 후 사용자 재시도를 요구한다.
- **BR-012**: safety failure는 실패로 보존하며 검증된 안전한 대체 서술만 허용한다.
- **BR-013**: 성공 GM Turn은 플레이어 의도를 반영하고 해결해야 한다.
- **BR-014**: 성공 GM Turn은 Meaningful Progress 범주를 최소 1개 가져야 한다.
- **BR-015**: `DECISION_REQUIRED`는 구체적 선택지 또는 판단 정보를 포함해야 한다.
- **BR-016**: StoryPlan `READY`는 Tactical Scene `READY`를 의미하지 않는다.
- **BR-017**: stage 진입은 필요한 Tactical Scene preparation을 시작한다.
- **BR-018**: 실제 총량을 모르면 수치 progress를 표시하지 않는다.
- **BR-019**: ResolvedTurnPlan은 prose와 독립적으로 저장·조회·재생할 수 있어야 한다.
- **BR-020**: presentation 전에는 world state, conversation, player-visible response를 commit하지 않는다.
- **BR-021**: rule/tool resolution은 idempotency key와 turn fingerprint로 중복 실행을 막는다.
- **BR-022**: presentation 재시도는 저장된 ResolvedTurnPlan을 재사용하고 rule/tool resolution을 반복하지 않는다.
- **BR-023**: Writer는 WriterContext와 ResolvedTurnPlan만 사용하며 prose 외 state delta/tool call을 반환할 수 없다.
- **BR-024**: public turn API 계약은 내부 lifecycle 분리와 무관하게 호환된다.
- **BR-025**: legacy 자료의 조회·재생은 묵시적 상태 변경이나 자동 재생성을 일으키지 않는다.
- **BR-026**: lifecycle diagnostics는 인증된 read-only 접근만 허용한다.
- **BR-027**: Prompt Candidate는 role, version, model, dataset, run과 연결되어야 한다.
- **BR-028**: 역할별 prompt/model은 독립 관리한다.
- **BR-029**: hard metric 악화 후보는 soft metric 상승만으로 승인할 수 없다.
- **BR-030**: optimization과 tuning은 holdout을 학습·선택에 사용하지 않는다.
- **BR-031**: tuning data는 provenance와 quality gate를 가지며 unsafe·미해결 오류 자료를 제외한다.
- **BR-032**: fine-tuning 선행 조건이 충족되지 않으면 tuning을 시작하지 않는다.
- **BR-033**: production prompt/model 후보는 baseline 대비 재현 가능한 결과와 rollback 경로를 가져야 한다.

## 7. States and State Transitions

### Candidate

```text
EXTRACTED → VALIDATED_COMPLETE
          → VALIDATED_PARTIAL → REPAIRING → COMPLETE | PARTIAL | INVALID
          → VALIDATED_INVALID → REPAIRING → COMPLETE | PARTIAL | INVALID
```

`NON_REPAIRABLE` candidate는 `REPAIRING`을 거치지 않는다.

### Compilation Outcome

```text
all complete                              → COMPLETE
only optional incomplete                  → COMPLETE_WITH_WARNINGS
one or more required candidates incomplete → FAILED
```

### GM Turn

```text
PLANNING → RESOLVED → PRESENTING → PRESENTED
    └────────→ FAILED      └────→ PRESENTATION_FAILED
```

- `RESOLVED`: rule/tool 결과와 State Delta가 확정됐지만 공개·commit 전이다.
- `PRESENTATION_FAILED`: bounded presentation 처리 실패. ResolvedTurnPlan은 보존된다.
- 동일 fingerprint 재요청은 저장된 상태에서 재개한다.

### Tactical Scene

```text
ABSENT → PREPARING → READY → ACTIVE
             └────→ FAILED
```

### Prompt Candidate

```text
DRAFT → EVALUATED → REJECTED
                  └→ PENDING_REVIEW → APPROVED → ACTIVE → ROLLED_BACK
```

### Fine-tuning Proposal

```text
NOT_JUSTIFIED → GATED → TRAINED → EVALUATED → REJECTED
                                          └→ APPROVED → ACTIVE → ROLLED_BACK
```

## 8. Failures, Exceptions, and Boundary Conditions

- repair 후에도 불완전한 candidate는 required/optional 정책으로 최종 판정한다.
- validation evidence 자체가 없으면 candidate를 추측으로 완성하지 않는다.
- GM narration이 안전하지만 플레이어 의도를 해결하지 않으면 실패다.
- 직전 turn과 의미적으로 같고 새 정보·결과·선택이 없으면 `NO_MEANINGFUL_PROGRESS`다.
- provider transient failure가 재시도 후에도 계속되면 retryable 외부 실패로 종료한다.
- Tactical Scene이 `ABSENT`, `PREPARING`, `FAILED`일 때 activation은 성공하지 않는다.
- progress 전체량이 0이거나 알 수 없으면 백분율을 만들지 않는다.
- 동일 fingerprint 중복 GM Turn 요청은 기존 결과를 반환하고, 다른 fingerprint 충돌은 명시적 conflict로 종료한다.
- WriterContext 경계 위반 또는 prose 외 Writer 결과는 공개하지 않는다.
- presentation 실패는 ResolvedTurnPlan을 보존하며 상태 변경·도구 실행을 반복하지 않는다.
- legacy 변환 실패는 추측·자동 재생성 없이 호환성 오류를 반환한다.
- prompt hard metric 회귀 또는 dataset leakage가 있으면 평가 후보나 run을 무효화한다.
- tuning data의 품질·권한·provenance가 불명확하면 학습에서 제외한다.
- tuned model의 비용·지연·holdout 결과가 허용 범위를 벗어나면 채택하지 않거나 rollback한다.

## 9. Inputs and Outputs

| Flow | Input | Output |
| --- | --- | --- |
| Candidate validation | 추출 후보, 근거, required 여부 | completeness, validation diagnostics, recoverability |
| Candidate repair | 불완전 후보와 진단 | repair 결과, 시도 횟수, 최종 후보 |
| Compilation policy | 최종 candidate 집합 | Compilation Outcome, warnings/failures |
| GM Turn verification | player intent, plan, resolution, narration, 최근 turn | 성공 결과 또는 stage별 failure |
| Tactical activation | stage와 scene readiness | activation 또는 `TACTICAL_SCENE_NOT_READY` |
| Progress | phase, completed units, total units | 실제 비율 또는 indeterminate 표시 |
| Runtime lifecycle | player action, idempotency, TurnPlan, rule/tool 결과, runtime state | lifecycle artifacts, ResolvedTurnPlan, presentation/commit 결과 |
| Prompt optimization | baseline/candidate prompt·config, model, train/dev/holdout, Eval rubric | hard/soft metric, baseline delta, review·registry 기록 |
| Fine-tuning gate | optimized baseline, provenance data, failure taxonomy, 비용·지연 기준 | 비교 평가, 역할별 채택·거부·rollback 결정 |

## 10. Scope and Non-goals

### Scope

- Scenario compilation candidate 진단, recoverability, bounded repair, package 결과 정책
- GM failure 분류·보존·외부 표현·재시도 정책
- player intent와 Meaningful Progress 검증
- Tactical Scene lazy readiness 계약
- phase/처리량 기반 progress 계약
- Resolved Turn 저장·재생, Writer 격리, lifecycle diagnostics와 legacy 호환
- 역할별 offline prompt optimization, Eval, approval, rollback
- 증거 기반 fine-tuning gate와 역할별 비교·rollback

### Non-goals

- AI 모델 또는 prompt 자체의 품질 튜닝
- 룰북에 없는 판정·근거 생성
- Tactical Scene eager pre-generation
- 운영자용 상세 진단 UI 전체 설계
- 자동 무한 retry 또는 임의 fallback narration
- 새로운 게임 규칙 또는 Eval Suite 최초 metric/contract 재설계
- online RL이나 사용자별 실시간 weight update
- 무검토 production prompt/model 자동 배포
- fine-tuning을 prompt optimization보다 먼저 적용

## 11. Priorities and Trade-offs

1. candidate 단위 진단과 GM failure 원인 보존
2. 상태 의미와 retryability 분리
3. bounded candidate repair와 Compilation Outcome 정책
4. Meaningful Progress 검증
5. Tactical lazy contract 정합성
6. 실제 phase 기반 progress
7. lifecycle/Writer 격리와 legacy 호환 유지
8. 역할별 prompt optimization과 registry
9. 조건부 fine-tuning gate

정확한 원인 보존과 결정론적 재현성을 처리 속도보다 우선한다. optional 결함은 경고와 함께 진행할 수 있지만 required 결함은 숨기지 않는다.

## 12. Success Conditions and Acceptance Criteria

- **AC-001 / G-001**: 실패한 compilation에서 운영자는 어떤 candidate가 어떤 validation code로 `PARTIAL` 또는 `INVALID`인지 조회할 수 있다.
- **AC-002 / G-001**: candidate 상태가 같더라도 required 여부에 따라 package 결과가 `FAILED` 또는 `COMPLETE_WITH_WARNINGS`로 달라진다.
- **AC-003 / G-002**: repairable candidate는 최대 1회 repair되고 시도 전후 결과가 보존된다.
- **AC-004 / G-002**: 동일 입력의 불완전 결과가 package worker에서 반복 자동 재시도되지 않는다.
- **AC-005 / G-003**: 모든 GM 실패는 내부 stage와 failure code를 가지며 외부 응답과 분리된다.
- **AC-006 / G-003**: transient provider 실패만 최대 1회 자동 재시도된다.
- **AC-007 / G-004**: player intent가 plan, resolution, narration 중 누락되면 GM Turn이 commit되지 않는다.
- **AC-008 / G-004**: Meaningful Progress 범주가 없으면 `NO_MEANINGFUL_PROGRESS`로 실패한다.
- **AC-009 / G-004**: 구체적 선택지나 판단 정보 없는 질문은 `DECISION_REQUIRED`로 인정하지 않는다.
- **AC-010 / G-005**: StoryPlan이 `READY`여도 Tactical Scene이 준비 전이면 activation은 stage와 readiness를 포함한 `TACTICAL_SCENE_NOT_READY`를 반환한다.
- **AC-011 / G-005**: stage 진입 후 Tactical Scene이 비동기 준비되고 `READY` 이후 activation할 수 있다.
- **AC-012 / G-006**: total units가 알려진 경우에만 실제 계산된 progress percentage가 표시된다.
- **AC-013 / G-006**: total units가 없으면 phase와 indeterminate 상태가 표시되며 임의 고정 percentage는 표시되지 않는다.
- **AC-014 / G-007,G-008**: Resolved Turn은 prose 없이 저장·조회·재생되며 Writer 실패 시 rule/tool과 상태 변경이 반복되지 않는다.
- **AC-015 / G-009**: Writer에 hidden/future/raw RAG/planner reasoning이 전달되지 않고 state delta/tool call 결과는 거부된다.
- **AC-016 / G-010**: 기존 public turn API와 legacy row/JSON의 조회·재생 계약이 유지된다.
- **AC-017 / G-011**: 인증된 read-only diagnostics에서 lifecycle artifact, failure stage, retry, verifier, commit 결과를 조회한다.
- **AC-018 / G-012,G-013**: baseline과 역할별 Prompt Candidate를 동일 Eval에서 비교하고 hard metric 회귀 후보를 탈락시킨다.
- **AC-019 / G-014**: prompt run의 dataset/model/version/metric/선택 사유를 재현하고 holdout 검증·승인·rollback할 수 있다.
- **AC-020 / G-015**: fine-tuning 전에 선행 조건과 미해결 failure category가 기록된다.
- **AC-021 / G-016**: tuned model은 base model + optimized prompt와 동일 조건으로 비교되며 hard constraint, holdout, 비용 기준을 모두 만족해야 역할별 채택된다.
