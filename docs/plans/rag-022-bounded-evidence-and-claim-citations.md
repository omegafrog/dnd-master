# RAG-022: 제한된 근거와 주장 단위 인용 검증

- 상태: `completed`
- 의존성: RAG-021
- Product Spec: UC-GM-001, BR-GM-003, BR-QUALITY-001, AC-GM-001~003
- Architecture Spec: Sections 3.5~3.6, 4.3, 5.4~5.5, 9.2, 11.2~11.3
- GitHub Issue: [#197](https://github.com/omegafrog/dnd-master/issues/197)
- Parent Issue: [#194](https://github.com/omegafrog/dnd-master/issues/194)

## 구현 목적

GM에게 검색 상위 결과를 무제한 전달하지 않고 현재 단계와 최신 행동 의도에 맞는 공개 근거를 최대 8개로 제한한다. 최종 응답의 인용은 Evidence Pack 안에 존재하는 것뿐 아니라 narration 또는 judgment의 구체적인 주장을 실제로 지지해야 한다.

## 사용자·엔티티 흐름

```text
플레이어 행동·현재 단계
→ intent/stage scoped evidence request
→ STORYBOOK 우선 + 조건부 RULEBOOK 최대 8개
→ provenance 보존 Evidence Pack
→ GmCitationBinding 후보
→ membership + claim support 검증
→ 검증된 GM turn 또는 FAILED_RETRYABLE
```

## 구현 범위

- current-stage, action-intent, session-document 범위를 가진 evidence request
- 전체 8개 상한과 현재 단계 STORYBOOK 필수 근거 우선순위
- 규칙 질문·판정에만 필요한 RULEBOOK 조건부 포함
- document/extractionVersion/locator/citation key 보존
- `GmCitationBinding`의 claim text, output field, citation key 계약
- pack membership와 claim relevance를 모두 확인하는 final validation
- 인용 없음, pack 밖 인용, 관련 없는 인용의 구조화된 위반
- evidence count, type, claim-support 결과 metric

## 수용 기준

- AI Game Master에 전달되는 근거는 모든 경로에서 8개를 넘지 않는다.
- 특정 방·적·보스 주장을 일반 룰북 장 설명만으로 통과시키지 않는다.
- citation key가 존재해도 바인딩된 주장을 지지하지 않으면 후보를 거부한다.
- 선택된 근거의 공개 provenance가 GM 후보와 최종 RuntimeTurn까지 유지된다.
- 적합한 근거가 없으면 임의 인용을 붙이지 않고 retryable failure 또는 정직한 무근거 응답 정책을 따른다.

## 테스트 계약

- 정책 단위 테스트: stage/intent 우선순위, 8개 상한, STORYBOOK/RULEBOOK 조합, claim support
- gateway 계약 테스트: request scope와 provenance round-trip
- validator 테스트: exact membership, unsupported binding, unrelated citation, Korean paraphrase fixture
- UI ↔ Entity E2E: 탐험·전투·규칙 질문 → RAG 검색 → 응답 주장 → 공개 chunk 인용 대조
- 회귀 테스트: RAG-018 server-owned citation key를 우회하거나 재작성하지 않음

## 제외 범위

- 임베딩·reranker 모델 교체
- fuzzy citation 복원
- 전투 계획 구조

## 완료 증거

- 질문 유형별 evidence selection fixture
- max-eight request/response capture
- 인용 membership·관련성 평가 결과

구현 및 검증:

- `RuntimeEvidenceSelector`가 stage/action-intent/session scope를 보존하고 STORYBOOK 우선, RULE/MIXED 조건부 RULEBOOK, 전체 최대 8개를 적용한다.
- `RuntimeEvidence`와 검색 gateway가 extraction version, locator, provenance identity, server-owned citation key를 round-trip한다.
- `GmCitationBinding`, `GmFinalValidator`, `GmValidationReport`가 output claim 존재, pack membership, claim support를 구조화된 violation과 metric으로 검증한다.
- WSL focused Java 회귀, AI/rule-knowledge 계약 회귀, gateway/entity 계약, UI `RuleEvidence` 2개와 typecheck, `graphify update .`, `git diff --check`가 통과했다.
- 전체 모듈의 기존 환경 회귀는 별도 기록했다: adventure `/v3/api-docs` HTTP 500, AI `ApiRequestGuard` 계약 환경 누락 3건.
