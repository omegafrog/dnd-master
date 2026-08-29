# Product Spec: GM Eval Suite v1

## 1. Problem and Context

AI Game Master 응답은 창의적 prose이므로 단일 정답 문자열로 품질을 판단할 수 없다. 동시에 이미 확정된 규칙 결과, 비밀 정보, 런타임 상태, Solo Player의 자율성은 prose 품질과 별개로 반드시 지켜야 한다. 모델, 프롬프트, TurnPlan 등 구조를 변경할 때 이 두 종류의 품질을 반복 가능하게 비교할 수 있는 turn-level GM Eval Suite가 필요하다.

v1은 하나의 GM Turn 응답만 평가한다. Scene/session 전체 평가나 운영 telemetry는 다루지 않는다.

## 2. Goals and Desired Outcomes

- G-EVAL-001: 버전된 데이터로 한 GM Turn 평가 사례를 독립적으로 표현한다.
- G-EVAL-002: hard constraint 위반과 창의적 품질 점수를 분리해 보고한다.
- G-EVAL-003: 기계적으로 판별 가능한 제약은 deterministic evaluator로 판정한다.
- G-EVAL-004: 주관적·의미적 품질은 anchored rubric 기반 LLM judge로 판정한다.
- G-EVAL-005: 단일 응답의 절대 평가와 동일 사례 두 응답의 pairwise 비교를 제공한다.
- G-EVAL-006: 모델·프롬프트·아키텍처 변경을 비교 가능한 benchmark와 run 기록을 제공한다.

## 3. Users and Actors

- **GM 개선 개발자**: 모델, 프롬프트, TurnPlan 유무 등 변경 전후 GM 응답을 평가·비교한다.
- **Eval Runner**: dataset과 generation/evaluation 설정으로 평가를 실행하고 구조화된 report를 만든다.
- **Deterministic Evaluator**: 명시된 hard expectation을 pass/fail violation으로 판정한다.
- **LLM Judge**: deterministic 판별이 불가능한 rubric dimension을 점수·근거·evidence로 판정한다.
- **Benchmark Maintainer**: 회귀·운영 발견 사례를 영구적인 versioned EvalCase로 추가한다.

## 4. Ubiquitous Language and Terminology

- **EvalCase**: 하나의 GM Turn을 평가하는 versioned benchmark 사례. 입력, context, hard expectation, quality rubric을 가진다.
- **EvalContext**: 평가 전 확정된 world/scene state, player knowledge, story stage, 선택적 TurnPlan 또는 resolved turn context.
- **Hard Expectation**: 위반 시 prose 점수와 독립적으로 실패해야 하는 명시적 제약.
- **Quality Rubric**: quality dimension별 anchored 점수 기준.
- **Hard Constraint Result**: expectation별 pass/fail 및 violation reason/evidence.
- **Quality Score**: rubric dimension별 score, reason, response evidence.
- **EvalResult**: 한 응답의 hard result와 quality score를 함께 표현한 결과.
- **Absolute Evaluation**: 하나의 response를 하나의 EvalCase와 비교하는 평가 방식.
- **Pairwise Evaluation**: 동일 EvalCase의 response A/B를 비교해 A, B, TIE와 dimension별 preference를 내는 방식.
- **Eval Run**: dataset/configuration/model/prompt/TurnPlan schema version을 고정한 평가 실행 단위.

## 5. Core Use Cases

### UC-EVAL-001: Hard constraint 절대 평가

1. 개발자가 EvalCase와 GM response를 제공한다.
2. Deterministic Evaluator는 표현 가능한 hard expectation을 검사한다.
3. Eval Runner는 expectation별 pass/fail, reason, evidence를 Quality Score와 분리해 반환한다.

### UC-EVAL-002: Rubric 기반 품질 평가

1. 개발자가 EvalCase와 GM response를 제공한다.
2. LLM Judge는 anchored Quality Rubric별 score, reason, response evidence를 반환한다.
3. high quality score는 hard constraint failure를 상쇄하지 않는다.

### UC-EVAL-003: Pairwise 변경 비교

1. 개발자가 동일 EvalCase에서 생성한 A/B response를 제공한다.
2. Eval Runner는 각 응답의 hard result와 applicable dimension preference를 평가한다.
3. overall winner A/B/TIE, dimension별 preference, 근거를 반환한다.

### UC-EVAL-004: Versioned benchmark 실행·비교

1. Benchmark Maintainer가 category별 handcrafted EvalCase를 versioned dataset에 유지한다.
2. 개발자가 dataset과 generation/evaluation configuration으로 Eval Run을 실행한다.
3. report는 hard pass/failure rate, category별 quality average, pairwise win/tie/loss 및 run metadata를 남긴다.

### UC-EVAL-005: 회귀 사례 영구화

1. 개발자가 발견한 GM regression을 한 Turn의 EvalCase로 정리한다.
2. 사례는 구현 코드와 독립된 versioned dataset에 추가된다.
3. 미래 Eval Run은 동일 사례를 다시 실행한다.

## 6. Business Rules and Invariants

- BR-EVAL-001: EvalCase는 player input, EvalContext, hard expectation, quality rubric을 표현한다.
- BR-EVAL-002: EvalContext는 world/scene state, player knowledge, current story stage를 표현하며 TurnPlan/resolved turn context는 선택이다.
- BR-EVAL-003: hard constraint result는 quality average와 분리된 expectation별 pass/fail violation이다.
- BR-EVAL-004: Rule Adherence, Information Control, State Consistency, Player Agency의 기계적으로 표현 가능한 제약은 deterministic evaluator를 우선한다.
- BR-EVAL-005: Information Control은 직접 누출과 semantic/indirect 누출을 구분한다. 구조화 Fact로 판별 가능한 직접 누출은 hard constraint다.
- BR-EVAL-006: Player Agency는 voluntary player decision, dialogue, belief, emotion, movement를 GM이 발명하면 위반이다. context의 explicit rule-driven forced effect는 허용한다.
- BR-EVAL-007: LLM judge의 모든 rubric은 generic 점수 지시가 아닌 anchored score definition을 가진다.
- BR-EVAL-008: LLM judge output은 dimension score, reason, response evidence를 포함한다.
- BR-EVAL-009: Pairwise comparison은 같은 EvalCase의 A/B response만 비교한다.
- BR-EVAL-010: Eval Run은 최소 case id, run id, timestamp, model, prompt/config version, TurnPlan/schema version when relevant, score, hard failure를 보존한다.
- BR-EVAL-011: versioned benchmark 데이터는 evaluator 구현과 분리한다.
- BR-EVAL-012: 하나의 EvalCase는 여러 category에 속할 수 있다.

## 7. States and State Transitions

| Current state | Operation | Next state |
| --- | --- | --- |
| versioned EvalCase | absolute evaluation | EvalResult with separate hard/quality results |
| same EvalCase + response A/B | pairwise evaluation | pairwise result with winner/preferences |
| dataset + configuration | runner execution | persisted Eval Run report |
| discovered regression | case authoring and versioning | reusable EvalCase in benchmark |

## 8. Failures, Exceptions, and Boundary Conditions

- EvalCase에 필요한 input/context/expectation/rubric이 없거나 version이 맞지 않으면 실행을 거부한다.
- deterministic evaluator가 expectation 표현을 지원하지 않으면 pass로 간주하지 않는다. unsupported/unevaluated 상태와 사유를 분리 보고한다.
- LLM judge가 structured output 또는 required evidence를 만들지 못하면 해당 quality result는 judge failure로 보고하며 hard result를 변경하지 않는다.
- response A/B가 서로 다른 case에 대응하면 pairwise evaluation을 거부한다.
- secret 본문 또는 state가 context에 없으면 evaluator는 추정으로 hard violation을 만들지 않는다.
- state/forced effect는 EvalCase에 명시된 runtime invariant/context만 권위로 삼는다.

## 9. Inputs and Outputs

| Input | Output |
| --- | --- |
| EvalCase + GM response | hard constraint results + quality scores |
| EvalCase + response A/B | overall winner, dimension preferences, reasoning/evidence |
| versioned dataset + generation/evaluation configuration | aggregate report + comparable run metadata |
| discovered regression details | reusable versioned EvalCase |

## 10. Scope and Non-goals

### In scope

- turn-level EvalCase model, hard constraints, quality rubrics, absolute/pairwise evaluation.
- deterministic mechanically-checkable checks.
- anchored structured LLM judge.
- v1 handcrafted benchmark: roughly 30-50 cases, Rule/State, Information, Agency, Continuity, Quality coverage each about 10; overlap allowed.
- runner, structured report, comparison metadata.

### Out of scope

- Planner/Writer architecture, narrative memory, Critic/Verifier rewrite loops, Best-of-N.
- style/example retrieval, prompt optimization, fine-tuning/preference tuning.
- production telemetry or automated production sample collection.
- scene-level/full-session evaluation.

## 11. Priorities and Trade-offs

1. Hard constraint detectability and independent reporting take priority over a single blended score.
2. Deterministic evaluation takes priority wherever structured expected facts/results make it reliable.
3. LLM judging covers semantic and subjective gaps, with evidence and anchored rubrics for auditability.
4. Dataset reusability across future GM architectures takes priority over coupling cases to current implementation.
5. v1 breadth is constrained to handcrafted turn cases; deeper scenario coverage follows later.

## 12. Success Conditions and Acceptance Criteria

- [ ] Versioned, implementation-independent turn-level EvalCase data exists.
- [ ] Hard constraint results and creative quality scores are always separate.
- [ ] Deterministic evaluators cover initial mechanically-checkable rule, information, state, and agency constraints.
- [ ] Anchored-rubric LLM judge produces structured dimension score, reason, evidence.
- [ ] Absolute evaluation repeatedly evaluates one response against one case.
- [ ] Pairwise evaluation compares two responses from one case with overall and dimension-level preferences.
- [ ] Seed benchmark has approximately 30-50 handcrafted cases across v1 categories.
- [ ] Runner report includes category breakdowns, hard rates, quality averages, and pairwise win/tie/loss.
- [ ] Run metadata supports model/prompt/architecture comparison over time.
