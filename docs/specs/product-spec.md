# Product Spec: Runtime narrative state, verifier, Best-of-N planning, and style exemplars

## 1. Problem and Context

현재 AI Game Master의 정적 Knowledge RAG와 실제 플레이 중 발생한 상태·지식·표현 품질이 충분히 분리되어 있지 않다. 그 결과 NPC가 모르는 정보를 사용하거나, 숨겨진 사실이 노출되거나, 확정 상태와 모순되는 서술이 생성될 수 있다. 단일 TurnPlan만으로는 후보 전략 비교와 좋은 GM 표현 참고도 해결되지 않는다.

이 스펙은 다음 네 기능을 하나의 생성 경계로 정의한다.

- #206: 세션별 Runtime Narrative State와 행위자별 지식 경계
- #207: Draft Response를 검사하는 Narrative Verifier와 최대 1회의 bounded rewrite
- #208: compact TurnPlan 후보를 생성·검증·선택하는 Best-of-N planning
- #209: 사실 검색과 분리된 GM style exemplar retrieval

핵심 흐름:

```text
Player Action → Runtime State / epistemic context → TurnPlan candidates
→ deterministic hard filter → Plan Judge → selected TurnPlan
→ GM Writer draft → Narrative Verifier → PASS 또는 최대 1회 rewrite
→ player response
```

## 2. Goals and Desired Outcomes

- G-RN-001: 세계의 확정 사실과 Player/NPC별 지식을 독립적으로 보존한다.
- G-RN-002: 정적 Knowledge와 Durable Runtime State, Working Memory, Transcript를 구분한다.
- G-RN-003: Writer 결과가 규칙·연속성·비밀·agency·NPC 지식 경계를 벗어나지 않게 검증한다.
- G-RN-004: 검증 실패 시 같은 Turn의 의미를 바꾸지 않는 rewrite를 최대 1회 수행한다.
- G-RN-005: 같은 hard constraints를 공유하는 compact TurnPlan 후보를 configurable한 N개 생성하고 하나를 선택한다.
- G-RN-006: 후보 선택은 prose가 아닌 계획을 대상으로 수행한다.
- G-RN-007: Knowledge RAG와 style exemplar retrieval을 provenance와 목적 기준으로 분리한다.
- G-RN-008: 비용·지연·실패 패턴을 관측할 수 있는 구조화된 결과를 남긴다.

## 3. Users and Actors

- **Solo Player**: 행동을 입력하고 공개 가능한 결과만 받는다. 자신의 선택은 시스템이 대신 확정하지 않는다.
- **AI Game Master / Planner**: Runtime State와 제한된 Knowledge를 사용해 TurnPlan 후보를 제안한다.
- **Plan Judge**: 유효 후보의 정합성·진행성·흥미를 비교해 하나를 선택한다.
- **GM Writer**: 선택된 TurnPlan, 상태, 근거, exemplar를 사용해 Draft Response를 만든다.
- **Narrative Verifier**: Draft Response를 deterministic 및 semantic 기준으로 검사한다.
- **Runtime Authority**: Resolved Turn과 State Delta를 검증·commit하는 정본 경계다.
- **Knowledge Retriever**: 정적 룰·시나리오·lore 사실을 제공한다.
- **Exemplar Retriever**: 표현 방식 참고용 curated GM 응답을 제공한다.

## 4. Ubiquitous Language and Terminology

- **Runtime Narrative State**: 세션 중 확정된 세계·플레이어·NPC·관계·단서·활성 스레드·최근 사건 상태.
- **World Fact**: 세계에서 참인 사실. 행위자가 알고 있는지와 별개다.
- **Revealed Fact**: Player에게 공개된 World Fact. 이후 다시 hidden으로 되돌아가지 않는다.
- **Character Knowledge**: 특정 Character가 알고 있다고 판정된 사실 목록과 별도 belief 목록.
- **Belief**: Character의 추측·오해·신념. World Fact로 승격되지 않는다.
- **Epistemic Boundary**: 어떤 actor가 어떤 Fact를 알거나 공개할 수 있는지의 경계.
- **Durable Runtime State**: 확정 세계 상태, 공개 사실, 지식, 관계, 활성 스레드처럼 장기 보존할 상태.
- **Working Memory**: 현재 장면과 최근 중요 사건의 제한된 판단용 상태.
- **Resolved Turn**: 판정과 상태 효과가 확정된 원자적 GM Turn 결과.
- **State Delta**: Resolved Turn에서 검증을 거쳐 Runtime State에 적용할 변경분.
- **Narrative Verification Context**: Draft, TurnPlan/Resolved Turn, Information Policy, 관련 상태·지식, Story Stage, rubric의 최소 검증 입력.
- **Hard Filter**: 명백히 무효인 후보를 Judge 전에 제거하는 결정적 검사.
- **Plan Judge**: 유효 TurnPlan 후보의 상대적 적합성을 평가하는 역할.
- **Style Exemplar**: 사실의 근거가 아니라 장면 구조·길이·리듬·상호작용 방식을 참고하는 curated GM 응답.
- **Provenance**: context 항목의 출처·목적·버전. Knowledge와 Exemplar는 서로 다른 provenance를 가진다.

## 5. Core Use Cases

### UC-RN-206-1: 비밀과 지식 비대칭 보존

1. Runtime Authority가 World Fact를 확정한다.
2. 해당 Fact의 공개 여부와 각 Character Knowledge를 별도로 기록한다.
3. `factsKnownBy(actorId)`, `characterKnows(characterId, factId)`, `canReveal(factId, actorId)`를 조회한다.
4. Writer와 NPC context에는 해당 actor가 허용받은 정보만 전달한다.

### UC-RN-206-2: State Delta commit

1. Resolved Turn이 State Delta를 만든다.
2. Delta를 검증한다.
3. Narrative State에 원자적으로 commit한다.
4. Transcript는 원본 기록으로 남지만 canonical state가 되지 않는다.

### UC-RN-207-1: Draft 검증과 제한적 rewrite

1. Writer가 선택된 TurnPlan으로 Draft Response를 생성한다.
2. Verifier가 최소 Context를 사용해 deterministic 및 semantic 검사를 수행한다.
3. PASS면 Draft를 반환한다.
4. ERROR가 있으면 동일 Turn 의미·규칙 결과·상태 효과를 보존하며 한 번 rewrite한다.
5. rewrite 후에도 실패하면 안전한 실패 결과를 반환하고 추가 loop는 실행하지 않는다.

### UC-RN-208-1: Best-of-N TurnPlan 선택

1. Planner가 동일 intent, state, stage, information boundary, rule capability를 공유하는 N개 후보를 만든다.
2. Hard Filter가 invalid 후보를 제거한다.
3. Plan Judge가 남은 후보를 평가한다.
4. continuity·agency·information safety를 우선하고, 동률이면 더 단순한 후보를 선택한다.
5. 선택된 TurnPlan만 Writer에 전달한다.

### UC-RN-209-1: Style Exemplar 검색·handoff

1. 현재 장면의 purpose, interaction type, tone, pacing, desired length로 ExemplarQuery를 만든다.
2. metadata filter, semantic retrieval, rerank로 작은 Top-K를 얻는다.
3. verifier ERROR 사례와 오염된/현재 세션 종속 exemplar를 제외한다.
4. Writer context에서 Grounded Knowledge와 Style Exemplars를 명시적으로 분리한다.
5. 적절한 exemplar가 없으면 exemplar 없이 계속한다.

## 6. Business Rules and Invariants

- BR-RN-001: World Fact와 actor knowledge는 서로 다른 상태다.
- BR-RN-002: Revealed Fact는 이후 replanning·compaction에서 hidden으로 되돌아가지 않는다.
- BR-RN-003: Character Knowledge와 Belief는 분리한다. Belief는 World Fact를 변경하지 않는다.
- BR-RN-004: Runtime State는 검증된 State Delta만 commit한다. 자유 자연어 history를 canonical state로 사용하지 않는다.
- BR-RN-005: 정적 Knowledge 검색 결과는 현재 Runtime State를 덮어쓰지 않는다.
- BR-RN-006: Verifier 입력은 필요한 최소 Context로 제한한다.
- BR-RN-007: Verifier ERROR는 rewrite 대상이다. WARNING만 있으면 반환 가능하다.
- BR-RN-008: rewrite는 동일 Resolved Turn의 표현만 바꾼다. 계획·규칙 결과·새 사건·State Effect·Story Stage를 바꾸지 않는다.
- BR-RN-009: 검증 흐름은 `1 draft + 1 verification + optional 1 rewrite`로 제한한다.
- BR-RN-010: Hard Filter 실패 후보는 Judge 점수 경쟁에 참여하지 않는다.
- BR-RN-011: 모든 Best-of-N 후보는 동일 hard constraints와 Player Intent를 공유한다.
- BR-RN-012: Writer에는 선택된 TurnPlan 하나만 전달한다. 후보 전체나 prose 후보를 전달하지 않는다.
- BR-RN-013: N은 configurable하다. 기본 검토값은 3이며 단순 턴은 N=1이 될 수 있다.
- BR-RN-014: Exemplar는 canonical fact source가 아니다. exemplar의 사건·entity·규칙은 현재 사실로 승격하지 않는다.
- BR-RN-015: Exemplar는 복사 대상이 아니다. 문장과 고유 표현을 그대로 재사용하지 않는다.
- BR-RN-016: Exemplar Top-K는 작고 configurable하다. 적절한 결과가 없으면 빈 목록을 허용한다.
- BR-RN-017: 관측 기록에는 모델/version, 후보·선택, 검증 위반, rewrite 결과, retrieval 결과, latency/token usage를 포함한다.

## 7. States and State Transitions

### Runtime Narrative State

```text
candidate delta → validated delta → committed durable state
                                      ├→ updated working memory
                                      └→ revealed/knowledge/relationship/thread updates
```

실패한 delta는 commit되지 않는다. Transcript append는 canonical state commit과 구분한다.

### Planning and Writing

```text
candidate plans → hard-filtered plans → judged plans → selected plan
selected plan → draft response → PASS
                              └→ ERROR → one rewrite → returned / safe failure
```

### Exemplar lifecycle

```text
candidate response → quality/provenance screening → curated exemplar → retrieved context
```

## 8. Failures, Exceptions, and Boundary Conditions

- 지식 경계 누락·충돌: actor context 생성을 거부하고 비공개 기본값을 적용한다.
- State Delta 검증 실패: 상태를 변경하지 않고 구조화된 rejection을 기록한다.
- 정적 문서와 runtime 상태 충돌: 현재 검증된 runtime 상태를 우선하고 provenance를 남긴다.
- Hard Filter 후 유효 후보가 0개: Writer를 호출하지 않고 planning failure를 반환한다.
- Judge 호출 실패 또는 동률: 안전성·연속성·agency·단순성 우선의 deterministic fallback을 사용한다.
- Writer 실패: Draft 없음으로 종료한다. 검증할 응답이 없으면 rewrite하지 않는다.
- Verifier 실패/timeout: 무검증 응답을 정상 반환하지 않고 bounded failure 정책을 적용한다.
- rewrite 후 ERROR 잔존: 추가 재시도 없이 실패 상태와 위반을 반환한다.
- Exemplar 없음·검색 실패·오염 후보만 존재: 빈 exemplar context로 Writer를 호출한다.
- Exemplar가 현재 session secret을 포함할 가능성: generic/anonymized exemplar를 우선하고 factual context보다 낮은 우선순위를 둔다.
- 모델 출력이 schema를 위반: deterministic parsing/validation 실패로 처리한다.

## 9. Inputs and Outputs

| Flow | Input | Output |
| --- | --- | --- |
| Runtime state | Resolved Turn, validated State Delta | committed Narrative State or rejection |
| Knowledge query | actor, fact policy, current state | scoped facts with provenance |
| Planning | Player Intent, state, stage, constraints, N | candidate TurnPlans, filter report, selected plan |
| Verification | Draft, selected plan/resolved turn, state, knowledge, rubric | PASS/FAIL, violations, rewrite decision |
| Rewrite | original draft, violations, same resolved turn | revised draft or bounded failure |
| Exemplar retrieval | ExemplarQuery, corpus policy, K | ranked exemplars with provenance/scores |
| Observability | flow events and model metadata | structured planning, verification, rewrite, retrieval records |

## 10. Scope and Non-goals

### In scope

- Runtime Narrative State and epistemic query/update contract.
- Verifier deterministic/semantic result contract and one-rewrite policy.
- Configurable plan-level Best-of-N generation, hard filtering, judging, tie-break.
- Separate Exemplar corpus/query/retrieval/writer handoff contract.
- Provenance, bounded context, fallback, and observability contracts.

### Out of scope

- TurnPlan schema 자체 재설계.
- 전체 Planner/Writer 모델 학습·fine-tuning·prompt optimizer.
- 무한 self-reflection, Best-of-N prose generation.
- 복잡한 감정 시뮬레이션 또는 vector DB 장기 episodic recall 최적화.
- GM Eval 전체 benchmark 설계.
- 자동 exemplar 승격을 위한 학습 정책.

## 11. Priorities and Trade-offs

- 정보 안전·규칙 정합성·Player Agency > 문체 품질·흥미.
- 구조화된 state/plan/result > 자유 자연어 memory.
- deterministic validation 우선, semantic LLM 판단은 애매한 품질 항목에 한정.
- plan-level 후보 비교 > prose-level 후보 생성. 비용과 판정 명확성 개선.
- 작은 N/K와 bounded retry > 최대 품질을 위한 무제한 호출.
- Knowledge와 Exemplar 분리 > 검색 인프라 공유보다 provenance 오염 방지 우선.
- generic/anonymized exemplar fallback > 현재 세션 비밀 오염 위험.

## 12. Success Conditions and Acceptance Criteria

- [ ] World Fact, Revealed Fact, Player Knowledge, NPC Knowledge, Belief를 독립 조회·표현한다.
- [ ] Runtime State는 검증된 State Delta만 commit하고 정적 RAG가 덮어쓰지 못한다.
- [ ] NPC별 지식 차이가 context에 자동 혼입되지 않는다.
- [ ] Verifier가 SECRET_LEAK, RULE_MISMATCH, PLAYER_AGENCY_VIOLATION, NPC_KNOWLEDGE_VIOLATION, TURNPLAN_DEVIATION, STATE_CONTRADICTION을 구조화해 보고한다.
- [ ] ERROR는 최대 한 번만 rewrite하고, rewrite가 Turn 의미를 변경하지 않는다.
- [ ] invalid 후보는 Judge 전에 제거된다.
- [ ] N=1/N=3 설정이 동작하고 Writer에는 선택 후보 하나만 간다.
- [ ] 후보 비교는 prose를 생성하지 않는다.
- [ ] Knowledge와 Exemplar가 별도 provenance로 Writer에 전달된다.
- [ ] 상황 metadata가 retrieval에 반영되고 Top-K가 제한된다.
- [ ] ERROR 응답은 exemplar 후보에서 제외된다.
- [ ] exemplar 부재·검색 실패 시 정상 fallback이 있다.
- [ ] 모델/version, 위반, rewrite, 후보, retrieval, latency/token 사용량을 관측할 수 있다.
