# 버그 보고서: UI 모험 계획 생성이 HTTP 500으로 차단됨

## 요약

실제 Playwright 브라우저로 룰북·스토리북 업로드부터 캐릭터·파티 구성까지 진행했으나, `모험 계획 만들기` 단계에서 HTTP 500이 발생했다. 모험이 시작되지 않아 GM 5턴, 전투, 내성 굴림, 다중 굴림, 숨김 정보 보호 검증은 실행할 수 없었다.

## 심각도

- Severity: Blocker
- 범위: UI 전체 여정의 Story Plan 생성 단계
- 재현율: 2/2 provider 설정에서 실패

## 검증 환경

- 검증일: 2026-08-07
- Frontend: `http://localhost:5173`
- Backend: `http://localhost:8080`
- Browser: Chromium headed, Playwright CLI
- 로그인: UI `테스트 계정으로 로그인`
- 직접 파일 수정: 없음
- 직접 DB 조회·수정: 없음

사용 자료:

- Rulebook: `docs/assets/dnd5th.pdf`
- Storybook: `docs/assets/892902-A_Most_Potent_Brew.pdf`

## 재현 절차

1. UI에서 테스트 계정으로 로그인한다.
2. 두 PDF를 파일 선택 UI로 업로드한다.
3. `dnd5th.pdf`를 `RULEBOOK`, `892902-A_Most_Potent_Brew.pdf`를 `STORYBOOK`으로 지정한다.
4. 문서가 `색인 완료 (v2)`가 됐는지 확인한다.
5. Storybook을 `MAIN_SCENARIO`, Rulebook을 `RULEBOOK`으로 번들 저장한다.
6. 시나리오 패키지를 컴파일한다.
7. 컴파일 완료 후 Blueprint를 게시한다.
8. 세션을 생성한다.
9. 캐릭터 `Arin`을 생성한다.
10. 인간·파이터·군인·질서 선을 선택하고 표준 배열 능력치를 배정한다.
11. 기술 숙련과 파이터 장비를 선택한다.
12. 캐릭터를 저장해 파티 `1/1`을 완성한다.
13. `모험 계획 만들기`를 클릭한다.

## 실제 결과

화면이 계속 다음 상태에 머문다.

```text
모험 계획 준비 중
파티와 모험 자료를 분석하고 있습니다.
```

브라우저 네트워크 증거:

```text
GET  /api/v1/adventure-sessions/{sessionId}/story-plan 500
POST /api/v1/adventure-sessions/{sessionId}/story-plan 500
```

실패 세션:

```text
sessionId: 3c40d9d5-3505-49f5-8f1f-7edac78d910e
bundleId: 4ce78359-73a6-4452-8692-12a878e137b4
scenarioPackageId: c7701fa8-66dc-4521-8136-0f32049b08ef
characterSheetId: 85825772-8a7c-4c76-8b5e-a571b1ebc749
```

## 재현 최소화

캐릭터 생성과 파티 구성은 성공했다.

```text
POST /internal/v1/adventure-sessions/{sessionId}/character-sheets 200
POST /api/v1/adventure-sessions/{sessionId}/party 200
```

따라서 실패 경계는 캐릭터·파티가 아니라 Story Plan 생성 호출이다.

provider를 UI에서 다음처럼 바꿔도 결과는 동일했다.

```text
ollama · qwen3:8b · medium → story-plan 500
openai · gpt-4o-mini · medium → story-plan 500
```

## 원인 분석

### 확인된 구조적 원인

세션 provider 전환은 `GmCompletionRouter`를 통해 일반 GM 턴에 적용된다. 그러나 Story Plan 전용 Bean은 router가 아니라 `SpringAiChatAdapter`를 직접 주입한다.

```java
// AiGameMasterApiConfiguration.java
@Bean
AdventureStoryPlanController aiAdventureStoryPlanController(
        SpringAiChatAdapter adapter,
        ObjectMapper objectMapper) {
    return new AdventureStoryPlanController(adapter, objectMapper);
}
```

컨트롤러도 직접 Ollama adapter를 호출한다.

```java
// AdventureStoryPlanController.java
return new Response(adapter.complete(request.operationId(), prompt, this::parse));
```

반면 provider router는 별도 Bean으로 존재한다.

```java
@Bean
@Primary
GmCompletionAdapter gmCompletionAdapter(
        SpringAiChatAdapter ollama,
        GmProviderProperties properties) {
    return new GmCompletionRouter(ollama, properties);
}
```

결과: UI에서 OpenAI provider를 선택해도 Story Plan 생성은 세션 binding을 읽지 않고 기본 Spring AI/Ollama 경로를 사용한다.

### 실패가 500으로 노출되는 이유

Adventure service gateway는 AI 내부 API의 비정상 응답을 일반 `IllegalStateException`으로 변환한다.

```java
if (response.statusCode() < 200 || response.statusCode() >= 300) {
    throw new IllegalStateException("story plan AI failed: " + response.statusCode());
}
```

Story Plan controller/application boundary에서 이 예외를 provider 장애 또는 명시적 `503 Service Unavailable`로 매핑하지 않아 UI에는 500으로 보인다.

## 경쟁 가설 및 판정

| 가설 | 판정 | 근거 |
|---|---|---|
| 파티가 미완성 | 기각 | UI에 `파티 1/1`, 파티 API 200 확인 |
| 캐릭터 저장 실패 | 기각 | 캐릭터 시트 API 200, 파티 API 200 |
| 번들/패키지 미컴파일 | 기각 | 패키지 `COMPLETE`, Blueprint 게시 성공 |
| OpenAI 설정 문제만 원인 | 기각 | Ollama와 OpenAI UI 선택 모두 실패 |
| Story Plan이 provider router를 우회 | 채택 | 전용 controller가 `SpringAiChatAdapter` 직접 호출 |
| AI 장애가 500으로 변환 | 채택 | gateway가 모든 non-2xx를 `IllegalStateException`으로 변환 |

## 기대 결과

1. Story Plan 생성이 세션 provider binding을 사용한다.
2. Ollama 또는 OpenAI 응답 성공 시 계획이 `READY`가 된다.
3. provider 장애는 500이 아니라 명시적 503/실패 상태로 표시된다.
4. UI가 재시도 버튼과 실패 원인을 표시한다.
5. 계획 성공 후에만 모험 시작과 GM 대화 화면으로 이동한다.

## 수정 제안

1. Story Plan controller가 `GmCompletionAdapter` 또는 provider-aware port를 주입받게 한다.
2. Story Plan 요청에 세션 provider/model/reasoning binding을 전달한다.
3. 전용 controller가 `GmCompletionRouter`를 통해 완료 요청을 실행하게 한다.
4. Story Plan generation gateway에서 provider failure를 도메인 오류로 분류한다.
5. HTTP boundary에서 provider timeout/rate-limit/unavailable를 503으로 매핑한다.
6. Story Plan provider 선택과 실패 매핑에 대한 integration test를 추가한다.

## 회귀 테스트 제안

### 단위/통합

- Story Plan controller가 `GmCompletionAdapter`에 provider selection을 전달하는지 검증
- `openai` 선택 시 Ollama adapter가 호출되지 않는지 검증
- AI 내부 API 503/500 응답이 adventure API에서 503 또는 명시적 failed plan으로 변환되는지 검증
- 정상 structured JSON이 4~6 stage와 2개 이상 ending을 통과하는지 검증

### 실제 Playwright

1. UI에서 provider를 `openai`로 전환한다.
2. `모험 계획 만들기`를 클릭한다.
3. Story Plan API가 2xx인지 확인한다.
4. 실패 시 화면에 500 raw JSON이 아닌 사용자용 오류가 표시되는지 확인한다.
5. 계획 성공 후 모험 시작 화면으로 이동한다.

## 검증 판정

재현 성공. 원인 후보를 코드 경계까지 좁혔다. 아직 코드 수정과 회귀 테스트는 수행하지 않았다. 따라서 GM 5턴 및 전투·굴림·숨김 정보 검증은 Story Plan 수정 후 재실행해야 한다.
