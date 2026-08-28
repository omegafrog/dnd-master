# RAG-026: 새 발행 기반 5턴 모험 품질 여정

- 상태: `completed`
- 의존성: RAG-020, RAG-021, RAG-022, RAG-023, RAG-024, RAG-025
- Product Spec: UC-QUALITY-001, BR-QUALITY-001, AC-GM-001~004, AC-PROVIDER-001, AC-QUALITY-001~002
- Architecture Spec: Sections 9.2~9.4, 11.2~11.7
- GitHub Issue: [#201](https://github.com/omegafrog/dnd-master/issues/201)
- Parent Issue: [#194](https://github.com/omegafrog/dnd-master/issues/194)

## 구현 목적

개발 RAG DB 데이터만 초기화한 뒤 공유 RULEBOOK과 Potent Brew STORYBOOK을 새 Python 전처리·Java 발행 파이프라인으로 다시 공개한다. 그 근거로 시나리오와 모험 계획을 새로 만들고, 모험 시작부터 서로 다른 5개 행동·질문까지 실제 provider로 실행해 전투 구조와 GM 응답 품질을 반복 가능한 release gate로 검증한다.

## 사용자·엔티티 흐름

```text
개발 RAG DB-only reset
→ 공유 RULEBOOK/STORYBOOK 새 preprocessing publication
→ 새 Scenario Package와 Adventure Story Plan
→ Combat Skeleton/tactical intent 검증
→ 모험 시작
→ 탐험·행동·전투·규칙·상태 질문 5턴
→ 응답·인용·provider·원자성 quality report
```

## 구현 범위

- docs/PDF 원본을 보존하는 개발 DB-only reset fixture
- 공유 RULEBOOK과 Potent Brew STORYBOOK의 새 전처리·vector publication 실행
- published extraction/chunk provenance 검증
- 새 scenario compilation과 story-plan generation
- rat/final-spider Combat Skeleton, Source Fact, fail-forward, tactical state 검사
- 5개 서로 다른 실제 플레이어 입력과 브라우저 진행
- 행동 반영률, neutral fallback 비율, citation exactness/relevance, provider 일치율, latency 계산
- forced provider/candidate failure에서 adventure version/conversation/stage 불변 검증
- 실행 provider/model과 raw adapter audit의 대조
- 재실행 가능한 명령, fixture, artifact, screenshot/report 보존

## 수용 기준

- reset은 DB 데이터만 제거하며 `docs/`와 원본 PDF를 삭제하지 않는다.
- 모든 검색 근거가 새로 발행된 extraction version과 chunk provenance를 가진다.
- 쥐와 최종 거미 단계가 완전하고 근거가 있는 Combat Skeleton을 가진다.
- 5개 턴 모두 최신 행동을 자연스러운 한국어 narration/judgment에 반영한다.
- 중립 narration/judgment 또는 자동 citation fallback이 0건이다.
- 모든 citation은 공개 chunk와 정확히 일치하고 바인딩된 주장을 지지한다.
- requested/effective provider와 실제 adapter invocation이 일치한다.
- 강제 실패는 adventure version, conversation, stage를 변경하지 않는다.

## 테스트 계약

- 정책 단위 테스트: 품질 metric 계산, denominator/empty-case, threshold, provider mismatch
- system integration: DB reset → preprocess → publish → retrieve provenance
- UI ↔ Entity E2E: 자료 준비 → 계획 생성 → 모험 시작 → 5턴 → 저장된 turn/plan/quality report 대조
- failure E2E: provider malformed/timeout fixture → FAILED_RETRYABLE → 상태 불변
- 회귀 검증: 기존 Potent Brew tactical browser journey와 published RAG contract 유지

## 제외 범위

- 모델 자체 교체나 특정 모델 고정
- human review를 모델 judge 점수로 대체
- 테스트를 통과시키기 위한 전용 production bypass

## 완료 증거

- WSL 전체 실행 명령과 서비스 preflight
- DB reset/publication/plan/5-turn 식별자와 quality report
- 실제 provider invocation 로그와 citation provenance 대조
- 브라우저 스크린샷 및 실패 원자성 DB snapshot
