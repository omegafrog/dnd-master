# Product Spec: Runtime Reveal Filter

## 1. Problem and Context

현재 TRPG 런타임의 판정과 서술 흐름은 세계의 내부 사실, 판정 절차, 플레이어 공개 정보를 분리하지 못할 수 있다. 숨겨진 대상, DC, 내부 비교값 또는 미발견 단서가 플레이어 출력에 섞이면 Solo Player는 캐릭터가 알 수 없는 메타정보를 얻게 된다. 반대로 행동 판정의 실행과 서술이 결합되면 상태 확정 전의 정보를 자연어로 노출할 위험이 있다.

## 2. Goals and Desired Outcomes

- **G-1** World Truth와 Player Knowledge를 분리하고, 플레이어에는 허용된 정보만 전달한다.
- **G-2** 트리거 감지, 판정 선택, 해결, 공개, 서술을 독립적으로 실행 가능한 단계로 만든다.
- **G-3** 플레이어 행동 기반 판정은 Solo Player가 굴리고, 세계/이벤트 기반 판정은 시스템이 해결한다.
- **G-4** Narration은 Player-visible Event 또는 State만 자연어로 표현한다.

## 3. Users and Actors

- **Solo Player**: 행동을 선언하고 필요한 경우 UI로 주사위 값을 제출하며, 자신의 캐릭터가 알 수 있는 결과만 받는다.
- **AI Game Master**: 제한된 플레이어용 정보를 자연어로 서술한다.
- **Trigger Agent**: 세계 상태 또는 플레이어 행동에서 판정 후보 발생 여부와 원인을 판단한다.
- **Check Selection Agent**: 후보가 판정인지, 판정 종류와 굴림 소유권 및 해결 방식을 판단한다.
- **Roll / Resolution Agent**: 주사위 또는 수동 수치로 내부 결과와 권위 상태 변경을 계산한다.
- **Reveal Agent**: 내부 결과와 권위 상태를 플레이어 공개 이벤트·상태로 변환한다.
- **Narration Agent**: 공개 이벤트·상태만으로 플레이어용 서술을 만든다.
- **Scenario Converter / LLM Validator**: Canonical Adventure Representation의 단서·판정·공개 관계를 보존하고 검증한다.

## 4. Ubiquitous Language and Terminology

- **World Truth**: 세계에는 존재하지만 아직 플레이어에게 공개되지 않았을 수 있는 정본 사실과 상태.
- **Player Knowledge**: Reveal Filter를 통과해 Solo Player에게 공개가 허용된 사실과 상태.
- **Trigger**: 판정 후보를 발생시키는 세계/이벤트 변화 또는 플레이어 행동.
- **World/Event-triggered Check**: 장소 진입, 시간 경과, NPC 행동, 적 접근, 함정 범위 진입 같은 세계 변화로 발생하는 판정.
- **Player-action-triggered Check**: 살피기, 듣기, 조사, 통찰 같은 Solo Player 행동 선언으로 발생하는 판정.
- **Roll Ownership**: Player-action-triggered Check는 Solo Player 소유, World/Event-triggered Check는 시스템 소유이며 애매하면 Solo Player 소유.
- **Authoritative State**: Resolution 이후 확정되는 내부 정본 상태.
- **Reveal Filter**: Authoritative State와 내부 결과에서 Player-visible Event 또는 State만 만드는 공개 정책.
- **Player-visible Outcome**: 제출한 주사위 값과 세계에서 관찰 가능한 행동 결과. 내부 판정 성공/실패, DC, 비교값, 미발견 정보는 포함하지 않는다.
- **Pending Roll Gate**: Solo Player 굴림 요청이 생성되면 제출 전에는 해당 GM Turn을 해결하거나 다음 진행으로 넘어갈 수 없는 상태.

## 5. Core Use Cases

### UC-1 세계 변화가 내부 판정을 유발한다

장소 진입 등 World/Event-triggered Trigger가 발생하면 Trigger Agent와 Check Selection Agent가 필요 시 판정을 선택한다. 시스템이 Resolution을 수행하고 Authoritative State를 갱신한다. Reveal Filter는 공개 가능한 결과만 전달하며 Narration은 그것만 서술한다.

### UC-2 플레이어 행동이 플레이어 굴림 판정을 유발한다

Solo Player가 행동을 선언하면 Trigger Agent가 후보를 만들고 Check Selection Agent가 불확실성·위험·숨겨진 정보·룰 조건이 있을 때만 판정을 선택한다. Player-action-triggered Check 또는 소유권이 애매한 판정은 Roll Request를 만들고 Solo Player에게 "지각 판정이 필요합니다. d20을 굴려 결과를 제출하세요." 수준의 정보만 보인다.

### UC-3 플레이어가 굴림을 제출해 행동을 해결한다

Solo Player가 요청된 d20 결과를 제출하면 Resolution이 내부 결과와 상태 변경을 계산한다. Reveal Filter 후에만 Narration이 실행된다. 플레이어는 제출값과 관찰 가능한 결과만 받는다.

### UC-4 숨겨진 단서 또는 위험 판정이 실패한다

숨겨진 대상, DC, 내부 실패 및 놓친 단서는 공개하지 않는다. 비밀문을 발견하지 못했으면 일반적 묘사만 보이며, 함정 탐지 실패는 함정 존재를 암시하지 않는다.

### UC-5 성공한 판정이 허용된 단서를 공개한다

성공 시에도 공개 조건과 허용된 정보 수준만 공개한다. 예를 들어 비밀문 단서는 책장과 바닥의 이상한 흔적으로, NPC 거짓말 단서는 허용된 수준의 불일치나 수상한 태도로 표현한다.

### UC-6 대기 중인 플레이어 굴림을 해결한다

Roll Request 생성 뒤에는 제출·Resolution 전까지 같은 GM Turn 및 다음 진행을 확정할 수 없다. 취소 또는 미제출은 Pending Roll Gate를 우회하지 않는다.

## 6. Business Rules and Invariants

- **BR-1** World Truth와 Player Knowledge는 별도 정보 집합이다.
- **BR-2** 숨겨진 대상의 존재·명칭·위치, DC, 내부 성공/실패 비교값, 실패한 대상, 미발견 단서, GM 전용 설명, 미래 정보는 Player-visible Event 또는 State에 포함하지 않는다.
- **BR-3** 판정 실패는 무엇을 놓쳤는지 암시하지 않는다.
- **BR-4** 행동 선언은 자동으로 판정이 되지 않는다. 불확실성, 위험, 숨겨진 정보 또는 룰 조건이 있을 때만 판정을 선택한다.
- **BR-5** Player-action-triggered Check는 Solo Player가 굴린다. 소유권이 애매한 경우도 Solo Player가 굴린다. World/Event-triggered Check는 시스템이 해결한다.
- **BR-6** Player Roll Request에는 판정 종류와 d20 제출 요청만 기본 공개한다. 숨겨진 실제 목적, DC, 대상, 위치, 비교값은 공개하지 않는다.
- **BR-7** 플레이어는 제출한 주사위 값과 관찰 가능한 결과만 받는다. 내부 기술적 성공/실패 표시는 하지 않는다.
- **BR-8** Authoritative State 갱신과 Reveal Filter가 완료되기 전 Narration은 실행되지 않는다.
- **BR-9** Narration Agent는 GM 전용 원문, 숨겨진 상태, 원본 RAG를 읽지 않고 Player-visible Event 또는 State만 읽는다.
- **BR-10** 판정·행동 명령 생성은 엔진 판정 전에 수행한다. 기존 결합 `Narration + Commands` 단계는 사용하지 않는다.
- **BR-11** Pending Roll Gate 중에는 상태 갱신, Reveal, Narration, 다음 GM Turn 확정이 일어나지 않는다.
- **BR-12** 턴 처리 실패 시 부분 상태·부분 공개·부분 서술을 남기지 않는다. 재시도 기준점은 턴 시작 직전 확정 상태다.
- **BR-13** Canonical Adventure Representation은 숨겨진 사실/대상, Trigger, 판정·해결 방식, 성공/실패 상태 변화, 공개 조건·수준, 기존 Player Knowledge를 보존한다.
- **BR-14** LLM Validator는 표현 누락·모순을 검증한다. 실제 런타임 정보 차단의 책임은 Reveal Filter에 있다.

## 7. States and State Transitions

GM Turn 공개 흐름:

- `TRIGGER_DETECTION`: World/Event 또는 Player Action에서 판정 후보를 탐지한다.
- `CHECK_SELECTION`: 판정 필요성, 종류, 해결 방식, Roll Ownership을 정한다.
- `PENDING_ROLL`: Solo Player 제출을 기다린다. Player-owned Check만 진입한다.
- `RESOLUTION`: 내부 결과를 계산하고 Authoritative State 변경을 준비한다.
- `AUTHORITATIVE_UPDATED`: 내부 상태를 확정한다.
- `REVEALED`: Reveal Filter가 Player-visible Event 또는 State를 만든다.
- `NARRATED`: Player-visible 정보만 자연어로 표현한다.
- `COMPLETED`: 플레이어 출력이 완료된 GM Turn.

전이:

- `TRIGGER_DETECTION → CHECK_SELECTION`: 판정 후보 발견.
- `CHECK_SELECTION → RESOLUTION`: 판정 없음 또는 시스템 소유 판정.
- `CHECK_SELECTION → PENDING_ROLL`: 플레이어 소유 또는 소유권이 애매한 판정.
- `PENDING_ROLL → RESOLUTION`: 유효한 굴림 결과 제출.
- `RESOLUTION → AUTHORITATIVE_UPDATED → REVEALED → NARRATED → COMPLETED`: 내부 해결·공개·서술 순서.
- 처리 실패 시 턴 시작 직전 확정 상태를 유지한다. 구체 재시도 횟수와 최종 오류 UX는 별도 범위다.

## 8. Failures, Exceptions, and Boundary Conditions

- 플레이어 굴림 미제출 또는 취소는 Pending Roll Gate를 유지하며 진행을 건너뛰지 못한다.
- Trigger가 발생해도 Check Selection이 판정 불필요로 결정하면 Roll Request 없이 안전한 결과를 진행한다.
- 숨겨진 정보 관련 성공/실패 모두 Reveal Filter를 거친다.
- 턴 중 내부 처리 실패는 플레이어에게 부분 결과를 보이지 않고 직전 확정 상태로 돌아간다.
- Reveal 또는 Narration 검토에서 비공개 정보가 발견되면 해당 결과는 플레이어 출력으로 사용하지 않는다.
- 재시도 횟수, 백오프, 최종 오류 메시지·복구 UI는 이 기능의 범위가 아니다.

## 9. Inputs and Outputs

입력: World/Event, Player Action, 현재 Authoritative State, Canonical Adventure Representation의 판정·단서 정의, 기존 Player Knowledge, 필요한 주사위 제출값.

내부 출력: Trigger 결과, Check Selection, Roll Request 또는 시스템 Resolution, 내부 결과, Authoritative State 변경.

플레이어 출력: 최소 Roll Request, Player-visible Event 또는 State, Player-visible Outcome 기반 Narration. 비공개 정보는 출력에서 제외한다.

## 10. Scope and Non-goals

범위: 런타임 단계 분리, Trigger 유형, Check Selection, Roll Ownership, Player Roll Request와 Pending Roll Gate, Authoritative State 후 Reveal Filter, 공개 전용 Narration, Canonical Adventure Representation·Validator 관계, 턴 처리 중 부분 공개 방지.

비범위: 독립 수동 Tester endpoint/UI, 재시도 횟수·백오프·최종 오류 UX, 일반적인 런타임 복구 시스템 설계, 새 게임 규칙 설계, 원본 RAG 또는 GM 시나리오 편집 UI, 자동화 테스트 의무화, 인간 GM 역할.

## 11. Priorities and Trade-offs

1. 비공개 정보 차단과 Player Knowledge 정확성이 서술 풍부함·처리 편의보다 우선한다.
2. 사용자 행동의 굴림 소유권이 자동 비밀 해결보다 우선한다.
3. 소유권이 애매하면 메타정보 노출보다 사용자 굴림을 선택한다.
4. 상태 확정과 공개 순서가 한 단계 통합 편의보다 우선한다.
5. 상세 복구 정책보다 부분 공개 없는 원자적 턴 결과를 우선한다.

## 12. Success Conditions and Acceptance Criteria

- **AC-1** World/Event-triggered Check와 Player-action-triggered Check가 Trigger 유형·원인과 함께 구분된다.
- **AC-2** 불확실성·위험·숨겨진 정보·룰 조건이 없는 행동은 Roll Request를 만들지 않는다.
- **AC-3** Player-action-triggered Check와 애매한 소유권 Check는 Solo Player Roll Request를 만든다.
- **AC-4** Player Roll Request는 판정 종류와 d20 제출만 표시하며 DC·실제 목적·대상·위치를 표시하지 않는다.
- **AC-5** 제출 전 또는 취소 후 Pending Roll Gate는 다음 진행을 허용하지 않는다.
- **AC-6** 비밀문 존재/DC/굴림 14 같은 내부 실패는 "특별히 눈에 띄는 것은 없습니다" 수준의 출력만 만든다.
- **AC-7** 함정 탐지 실패 출력은 함정 또는 놓친 정보를 언급·암시하지 않는다.
- **AC-8** 발견 성공 출력은 공개 수준에 맞는 단서만 전달한다.
- **AC-9** Narration 입력에는 Player-visible Event 또는 State만 있으며 GM 원문, 숨겨진 상태, 원본 RAG는 없다.
- **AC-10** 명령 생성은 Resolution 전에, Narration은 Authoritative State 갱신과 Reveal Filter 뒤에 실행된다.
- **AC-11** Canonical Adventure Representation과 Validator 검증 결과가 요구된 Trigger·판정·상태 변화·공개 관계를 보존한다.
- **AC-12** 턴 처리 실패는 부분 상태·부분 공개·부분 Narration을 남기지 않는다.
