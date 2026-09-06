# Combat UI 구현 계획

Parent: [#286](https://github.com/omegafrog/dnd-master/issues/286)

관련 명세:

- [Product Spec](../specs/combat-ui/product-spec.md)
- [Architecture Spec](../specs/combat-ui/architecture-spec.md)

다이어그램:

- [Product Use Case](../specs/combat-ui/diagrams/product/UC-CUI.usecase.svg)
- [Product Activity](../specs/combat-ui/diagrams/product/UC-CUI.activity.svg)
- [Architecture Class](../specs/combat-ui/diagrams/architecture/adventure-runtime.class.svg)
- [Architecture State](../specs/combat-ui/diagrams/architecture/combat-encounter.state.svg)

## 실행 순서

1. [#279 — 전투 진입·Initiative·Snapshot·Mode 전환](https://github.com/omegafrog/dnd-master/issues/279) — 의존성 없음
2. [#280 — Human action·자원·명시적 Turn 종료](https://github.com/omegafrog/dnd-master/issues/280) — #279
3. [#281 — Map·Mapless 이동](https://github.com/omegafrog/dnd-master/issues/281) — #280
4. [#282 — 자유 행동 선언](https://github.com/omegafrog/dnd-master/issues/282) — #279, #280
5. [#283 — Reaction interrupt·정확한 resume](https://github.com/omegafrog/dnd-master/issues/283) — #280, #281
6. [#284 — AI 자동 진행·Retry·Failure Recovery](https://github.com/omegafrog/dnd-master/issues/284) — #280, #283
7. [#285 — 전투 종료·최종 상태·일반 세션 복귀](https://github.com/omegafrog/dnd-master/issues/285) — #280, #284

각 Sub-issue가 해당 slice의 구현 목적, 범위, 수용 기준, 정책 단위 테스트, `ui ~ entity` E2E 계약의 정본이다. GitHub Parent/Sub-issue 관계와 Project 5의 `Workflow Status`가 추적 정본이다.

## 전투 UI 개선 보완 계획

현재 전투 화면은 진입과 턴 순환은 가능하지만, 실제 행동을 선택하고 결과를 확인하는 흐름이 끊겨 있다. 다음 순서로 개선한다.

### P0-1. 공격 행동 계약과 대상 선택

- `CombatScreen`에 공격 가능한 적 대상 선택 UI를 추가한다.
- 스냅샷 또는 별도 옵션 API가 플레이어에게 공개 가능한 `targetOptions`와 `availableActions`를 제공한다.
- 공격 요청은 actor·action·target 중심으로 단순화하고, AC·공격 보정·피해값은 서버가 캐릭터/규칙 경계에서 계산한다.
- 대상 미선택·행동 불가 상태에서는 버튼을 비활성화하고, 409/422의 안정적인 오류 코드와 위반 사유를 사람이 읽을 수 있게 표시한다.

### P0-2. 전투 화면 정보 구조와 전장

- 헤더, Initiative, 자원, 전장, 행동 패널, 전투 타임라인을 responsive grid로 재구성한다.
- Combat Mode에서도 맵을 유지하고, 맵 토큰 선택을 대상 선택·이동 목적지와 연결한다.
- mapless 전투는 range/cover와 서술 위치를 같은 행동 흐름에서 표시한다.
- 현재 턴, AI 처리 중, Reaction 대기, 명령 처리 중 상태를 명시한다.

### P0-3. 상태 동기화와 이동

- `useCombatSession`을 도입해 snapshot 초기화, SSE cursor, 명령 후 재조회, stale version 복구를 한 곳에서 관리한다.
- 좌표 문자열 입력을 맵 목적지 선택으로 대체하고, mapless는 공개된 관계 선택 또는 자유 선언으로 처리한다.
- Action/Bonus Action/Reaction/이동 자원을 별도 상태로 표시하고 중복 제출을 막는다.

### P1. 지속 가능한 기록과 복구 UX

- 컴포넌트 로컬 로그를 snapshot·SSE 기반 Combat Timeline으로 대체해 새로고침/재진입 후에도 복원한다.
- 자유 행동, Reaction dialog, AI 실패 재시도를 같은 상태 모델로 통합한다.
- 모바일 레이아웃과 키보드 포커스·status live region을 검증한다.

필수 검증은 대상 선택 전 공격 불가, 대상 선택 후 target 포함 요청, 명령 후 자원/턴 갱신, AI 턴 진행, 맵·mapless 이동, 새로고침 복원, 409/422 오류 안내를 실제 브라우저에서 확인하는 것이다. 주요 변경 경계는 `CombatScreen.tsx`, `CombatApi.ts`, `AppShell.tsx`, `CombatMapView.tsx`와 전투 snapshot/controller 계약이다.
