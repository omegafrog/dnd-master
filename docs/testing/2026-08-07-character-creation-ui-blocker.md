# 버그 보고서: 게시된 Blueprint 기반 캐릭터 생성이 UI에서 차단됨

## 요약

정상 UI 흐름으로 룰북·스토리북을 업로드하고 시나리오 패키지를 컴파일한 뒤 캐릭터 생성 설정을 게시하면, 캐릭터 선택 변경 요청이 HTTP 500으로 실패한다. 최종 캐릭터 저장도 HTTP 400으로 실패한다.

이 문제로 캐릭터 및 파티 생성, 모험 시작, GM 대화 진입이 모두 차단된다.

## 심각도

- Severity: Blocker
- Priority: Highest
- 재현율: 2/2 캐릭터 저장 시도 실패
- 범위: 실제 백엔드와 연결된 웹 UI 전체 여정

## 검증 환경

- 검증일: 2026-08-07
- Frontend: `http://127.0.0.1:5173`
- Backend: `http://localhost:8080`
- Browser: Chromium 150, Playwright CLI headed mode
- 사용자: UI의 `테스트 계정으로 로그인`
- 데이터베이스: 기존 DB와 분리된 신규 Compose 볼륨. 앱 Flyway 마이그레이션으로만 초기화
- DB 직접 조회·수정: 없음
- 입력 파일 수정: 없음

사용 자료:

- Rulebook: `docs/assets/dnd5th.pdf`
- Storybook: `docs/assets/892902-A_Most_Potent_Brew.pdf`

## 사전 조건 및 성공한 준비 흐름

1. UI에서 테스트 계정으로 로그인한다.
2. 두 PDF를 한 번에 업로드한다.
3. `dnd5th.pdf`를 `RULEBOOK`으로 지정한다.
4. `892902-A_Most_Potent_Brew.pdf`를 `STORYBOOK`으로 지정한다.
5. 두 문서가 `색인 완료 (v2)`가 될 때까지 기다린다.
6. Storybook은 `MAIN_SCENARIO`, Rulebook은 `RULEBOOK` 역할로 번들을 저장한다.
7. 시나리오 패키지를 컴파일한다.
8. 컴파일 결과가 `PUBLISHED`, 패키지가 `COMPLETE`임을 확인한다.
9. `캐릭터 생성 시작`을 누른다.
10. 캐릭터 생성 설정 검토 화면에서 `검토 완료 후 게시`를 누른다.
11. `세션 생성 후 캐릭터 만들기로 이동`을 누른다.

생성된 테스트 식별자:

- Bundle: `f649a18b-c8a7-43f8-bf8d-b30ec30094c1`
- Scenario package: `c3425a7d-d9e7-4459-a5e1-16341498c2b1`
- Session: `94f22d9d-2fee-465b-85e9-d912b12fcafa`

## 재현 절차

1. 캐릭터 이름으로 `브룸`을 입력한다.
2. 종족 `드워프`, 하위 종족 `언덕 드워프`를 선택한다.
3. 클래스 `파이터`, 배경 `군인`, 성향 `질서 선`을 선택한다.
4. 능력치 화면에서 `4d6 굴림(최저값 제외)`을 실행한다.
5. 기술 숙련을 선택한다.
6. `파이터 시작 장비`를 선택한다.
7. `캐릭터 저장하기 →`를 누른다.

## 실제 결과

### 결함 1: 게시된 Blueprint 변경 요청이 HTTP 500

캐릭터 생성 설정을 게시한 이후 종족, 하위 종족, 클래스, 배경, 성향 등을 선택할 때마다 다음 요청이 반복 실패한다.

```text
POST /api/v1/scenario-packages/{packageId}/character-blueprint/resolve
500 Internal Server Error
```

서버 예외:

```text
java.lang.IllegalStateException: published blueprint is immutable
  at CharacterCreationBlueprint.resolve(...)
  at CharacterCreationBlueprint.resolveNode(...)
  at ScenarioPreparationApplicationService.resolveBlueprint(...)
```

UI는 게시된 Blueprint를 캐릭터 생성의 읽기 전용 스키마로 사용해야 하지만, 실제 선택마다 패키지 Blueprint 자체를 resolve하려 시도한다.

### 결함 2: 화면 HP와 저장 payload 불일치 후 HTTP 400

화면 미리보기에는 다음 값이 표시됐다.

```text
HP 10/10
AC 18
주도권 +2
```

그러나 저장 요청 payload에는 다음 값이 포함됐다.

```json
{
  "characterState": "{\"equippedItems\":{\"armor\":\"가죽 갑옷\",\"shield\":true},\"currentHitPoints\":0,\"temporaryHitPoints\":0,\"experience\":0}"
}
```

저장 요청:

```text
POST /internal/v1/adventure-sessions/{sessionId}/character-sheets
400 Bad Request
```

UI 메시지:

```text
캐릭터 시트를 생성하지 못했습니다.
```

같은 버튼을 다시 눌러도 동일하게 실패했다.

### 추가 관찰

- 장비 UI에서 `파이터 시작 장비` 선택 후에도 기존 단검, 지팡이, 가죽 갑옷과 파이터 장비가 함께 payload에 포함됐다.
- `equippedItems.armor`는 가죽 갑옷인데 화면 AC는 방패 포함 18로 표시됐다. 표시 계산과 저장 상태의 장비 정합성도 확인이 필요하다.
- 능력치 굴림과 화면 파생 수치 계산 자체는 동작했다.

## 기대 결과

1. 게시된 Blueprint는 변경하지 않고 세션별 캐릭터 입력 상태만 갱신한다.
2. 캐릭터 선택 과정에서 HTTP 500이 발생하지 않는다.
3. 화면에 HP `10/10`이 표시되면 저장 payload의 `currentHitPoints`도 `10`이어야 한다.
4. 장착 장비, AC 계산, 저장 payload가 동일한 상태를 표현해야 한다.
5. 캐릭터 저장 성공 후 1명 정원의 파티가 완성되어 모험 시작 화면으로 이동해야 한다.

## 사용자 영향

완전 차단:

- 캐릭터 생성 불가
- 파티 생성 불가
- Adventure Start Lock 진입 불가
- 모험 시작 불가
- GM 대화, 전투, 굴림 및 비밀 정보 노출 정책 검증 불가

## 추정 원인

두 상태 모델이 혼합된 것으로 보인다.

1. 패키지 수준의 게시된 `CharacterCreationBlueprint`
2. 세션 수준의 캐릭터 생성 입력/선택 상태

프런트엔드가 캐릭터 선택 시 1번을 변경하는 resolve API를 호출한다. 백엔드는 게시 후 불변성을 올바르게 거부하지만 이를 500으로 노출한다. 동시에 화면 계산 결과가 최종 `characterState` 직렬화에 반영되지 않아 HP 0과 장비 불일치가 발생한다.

## 수정 제안

1. 캐릭터 생성 페이지에서 게시된 Blueprint resolve 호출을 제거한다.
2. 선택값은 로컬 draft 또는 세션 전용 draft API에 기록한다.
3. 서버가 `published blueprint is immutable`을 500 대신 명시적 도메인 오류로 반환하도록 방어한다.
4. 저장 직전 단일 계산 결과에서 `characterBuild`, `characterState`, 화면 미리보기를 함께 생성한다.
5. 초기 `currentHitPoints`를 계산된 최대 HP로 설정한다.
6. 장비 묶음 선택 시 교체/병합 규칙을 일관되게 적용하고 장착 갑옷과 AC를 동일 소스에서 계산한다.

## 회귀 테스트 제안

Playwright 실제 백엔드 테스트로 다음 수직 흐름을 추가한다.

1. Blueprint 게시
2. 세션 생성
3. 종족·클래스·배경·능력치·기술·장비 선택
4. 선택 과정의 모든 `/resolve` 요청이 2xx이거나 호출되지 않음을 검증
5. 화면 HP와 저장 요청 `currentHitPoints` 일치 검증
6. 캐릭터 저장 2xx 및 파티 정원 충족 검증
7. 모험 시작 버튼 활성화 검증

## 증거

- Screenshot: `output/playwright/full-ui-journey/.playwright-cli/page-2026-08-07T04-04-18-709Z.png`
- Playwright trace: `output/playwright/full-ui-journey/.playwright-cli/traces/trace-1786074978713.trace`
- Network log: `output/playwright/full-ui-journey/.playwright-cli/traces/trace-1786074978713.network`

## 판정

현재 버전은 “파일 인덱싱부터 캐릭터·파티 생성, 모험 시작, GM 대화” 전체 UI 여정을 지원하지 않는다. 캐릭터 생성 저장 차단을 수정하기 전에는 후속 GM 품질 검증을 진행할 수 없다.

## 진단 및 수정 결과

재현 후 코드와 요청 payload를 대조해 원인을 확정했다.

- HTTP 400의 직접 원인은 `STANDARD_ARRAY_MISMATCH`다. UI의 `4d6 굴림(최저값 제외)`이 예를 들어 `12,14,9,8,15,7`을 생성하지만, 서버 검증기는 `15,14,13,12,10,8` 표준 배열만 허용했다.
- `characterState.currentHitPoints=0`도 실제 결함이었다. 화면 계산 HP를 저장 상태에 반영하지 않았다.
- 캐릭터 생성 페이지가 게시 완료 후에도 `character-blueprint/resolve`를 호출해 게시된 Blueprint 불변성 예외를 유발했다.

수정:

- 캐릭터 생성 페이지의 선택값을 패키지 Blueprint에 resolve하지 않도록 변경. 선택값은 세션 캐릭터 draft에만 유지한다.
- `ROLL_4D6_DROP_LOWEST` 방법을 build의 `ruleChoices.abilityScoreMethod`에 기록하고, 서버가 해당 방법의 3~18 범위 점수를 허용하도록 변경.
- 저장 시 `currentHitPoints`를 계산된 1레벨 최대 HP로 설정.

회귀 검증:

- `./gradlew :character-management-service:test --tests 'com.dndmaster.character.api.Dnd5e2014CharacterCreationValidatorTest'` 통과
- `npm test -- --run src/features/character/CharacterCreationPage.test.tsx` 통과 (8 tests)
- `npm run typecheck` 통과

수정 후 실제 Playwright 전체 여정 재실행과 GM 5턴 검증은 별도 재검증이 필요하다.
