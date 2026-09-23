# ADR-022: Codex 구조화 출력 스키마 계약

## 상태

Accepted

## 배경

AI Game Master는 Codex App Server의 `turn/start.outputSchema`를 사용해 리랭커와 근거 충분성 판정의 응답 형식을 제한한다. Codex는 일반 JSON Schema 전체가 아니라 구조화 출력에서 지원하는 일부 규칙만 허용한다. 특히 모든 객체에 `additionalProperties: false`를 지정하고, 선언한 필드를 모두 `required`로 지정해야 한다.

기존 근거 충분성 판정 스키마는 `selectionReasons`를 후보 ID를 키로 하는 임의 키 객체로 표현했다. 이 형태는 Codex가 생성할 키를 미리 고정할 수 없고, `additionalProperties`에 문자열 스키마를 넣기 때문에 요청 단계에서 `invalid_json_schema`로 거부될 수 있다.

## 결정

- Codex 구조화 출력의 공식 규칙을 내부 AI 응답 스키마의 기준으로 삼는다.
- 리랭커의 `orderedCandidateIds` 응답은 고정된 배열·문자열 원소·필수 필드·`additionalProperties:false`를 유지한다.
- 근거 충분성 판정의 Codex 응답에서 `selectionReasons`는 임의 키 객체가 아니라 다음 고정 객체의 배열로 표현한다.

```json
"selectionReasons": [
  { "evidenceId": "문자열", "reason": "문자열" }
]
```

- 배열 원소 객체에도 `required: ["evidenceId", "reason"]`와 `additionalProperties:false`를 지정한다.
- AI Game Master 내부 파서는 배열을 기존 `Map<String, String>`으로 변환한다. 따라서 adventure-service와 외부 내부 HTTP 응답의 ID→이유 계약은 유지한다.
- 선택된 근거 ID와 이유의 일치, 고정된 근거 ID 유지, 빈 이유 금지 검증은 기존 서버 검증을 계속 적용한다.

## 결과

- 충분성 판정 요청은 Codex의 객체 규칙을 만족하므로 응답 생성 전 스키마 거부를 피할 수 있다.
- 후보 ID처럼 실행마다 달라지는 값은 객체 키가 아니라 배열 원소의 값으로 전달한다.
- 리랭커와 충분성 판정은 같은 근거 확보 흐름에서 순차 실행하지만, 각각 독립된 Codex 호출과 독립된 응답 스키마를 사용한다.
- 외부 DTO와 저장 모델을 변경하지 않아 adventure-service의 기존 계약과 선택 이유 조회 코드를 유지한다.

## 공식 참고 문서

- [OpenAI Structured Outputs 공식 문서](https://developers.openai.com/api/docs/guides/structured-outputs): 지원 타입, 최상위 객체, 필수 필드, `additionalProperties:false`, 제한된 JSON Schema 규칙.
- [Codex App Server 공식 문서](https://learn.chatgpt.com/docs/app-server?translationFallback=ko-KR): `turn/start.outputSchema`와 현재 턴에 적용되는 구조화 출력 예시.
- [Codex 비대화형 모드 공식 문서](https://learn.chatgpt.com/docs/non-interactive-mode?translationFallback=es-419): `codex exec --output-schema` 사용법.

