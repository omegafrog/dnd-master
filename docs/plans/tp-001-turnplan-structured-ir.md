# TP-001: GM TurnPlan structured narrative IR

- Issue: [#213](https://github.com/omegafrog/dnd-master/issues/213)
- Parent: [#203](https://github.com/omegafrog/dnd-master/issues/203)
- Status: `ready-for-agent`
- Dependencies: none

## 구현 목적

AI Game Master가 Solo Player 응답 전 한 GM Turn의 의미를 구조화된 `TurnPlan`으로 만들고 검증할 수 있게 한다. 플레이어 의도, 판정 요청, 서사 의도, 정보 경계, 상태 효과, Story 진행을 final prose·규칙 결과·상태 commit과 분리한다.

## Implementation Scope

- `ai-game-master-service`에 `com.dndmaster.aigamemaster.domain.turnplan` 추가.
- immutable Java record: `TurnPlan`, `PlayerIntent`, `ResolutionRequest`, `NarrativeIntent`, `InformationPolicy`, `StateEffect`, `StoryProgress`.
- 제한된 enum: `ResolutionType`, `ScenePurpose`, `NarrativeTone`, `NarrativePacing`.
- `TurnPlanValidator`와 validation error 추가.
- local field validation은 record compact constructor에서 수행.
- cross-field validation은 Fact ID 집합 충돌과 graph invariant만 수행.
- Spring-managed Jackson `ObjectMapper`로 JSON deserialize/serialize한다. custom serializer/adapter 추가 금지.
- `src/test/resources/turnplan/`에 canonical fixture 5개 추가.

## Acceptance Criteria

- `schemaVersion`은 정확히 string `"1"`이다.
- 6개 TurnPlan 영역은 각각 typed field를 가진다.
- `requiredFacts ∩ forbiddenFacts = ∅`, `revealableFacts ∩ forbiddenFacts = ∅`를 강제한다.
- 각 Fact ID 및 Story condition ID 컬렉션 중복을 거부한다.
- `PlayerIntent`는 action, goal, target을 표현하며 입력 밖 행동/결과 필드를 갖지 않는다.
- `ResolutionRequest`는 request 의미만 표현하며 roll, success/failure, damage, 확정 상태 필드를 갖지 않는다.
- action ID, target ID, Fact ID, state effect type, Story condition ID는 열린 정규화 string이다.
- `StateEffect`는 `type`, `target`, `from`, `to`를 표현하며 실제 상태 commit을 수행하지 않는다.
- 관찰, Perception 판정, 정보 비대칭, 상태 변화, Story 진행 fixture가 JSON round-trip을 통과한다.
- 잘못된 required field, schema version, ID, 중복, Fact 충돌, unknown final-prose/result field는 거부한다.

## Test Contract

### Policy-based unit tests

- record 별 null/blank/empty/defensive-copy 규칙.
- schema version, enum, normalized ID 규칙.
- Fact 집합 충돌·중복, Story condition 중복.
- Resolution request와 final result 분리.
- external target/Fact/Story Plan 존재 조회를 수행하지 않음.

### Boundary E2E

UI/API는 이 slice에서 금지된다. 대체 경계 E2E:

`fixture JSON → Spring ObjectMapper → TurnPlan entity graph → TurnPlanValidator → JSON → equivalent TurnPlan`

모든 canonical fixture와 malformed fixture가 이 경계를 검증한다.

## Forbidden Scope

- `GmAgentController`, `GmCompletionAdapter`, provider prompt/repair lifecycle 변경.
- `adventure-service`, `RuntimePlan`, `HttpGmAgentPort`, runtime validator 변경.
- DB/repository/migration/API/message 변경.
- Rule Engine handoff, Writer generation, Planner orchestration, Eval/Critic.

## Verification

```bash
cd /home/jiwoo/workspace/dnd-master
export PATH=/home/jiwoo/.nvm/versions/node/v24.12.0/bin:/home/jiwoo/.sdkman/candidates/java/current/bin:/home/jiwoo/.local/bin:$PATH
export JAVA_HOME=/home/jiwoo/.sdkman/candidates/java/current
./gradlew :src:ai-game-master-service:test
git diff --check
graphify update .
```
