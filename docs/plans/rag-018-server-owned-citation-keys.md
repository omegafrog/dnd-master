# RAG-018: 서버 소유 citation key로 모험 계획 근거 정규화

- Issue: —
- Parent: #189
- Status: in-progress
- Dependencies: RAG-016

## 구현 목적

모험 계획 생성 모델이 긴 문서 locator, chunk ID, quote를 직접 복사하지 않도록 하고, 서버가 검색 결과의 정본 citation을 보존한다. 모델이 페이지 번호나 chunk ID를 변조해도 존재하지 않는 인용으로 오인되는 대신 서버의 허용된 citation만 최종 계획에 들어가게 한다.

## 구현 범위

- 모험 계획 요청의 citation 목록에 요청 범위의 안정적인 citation key를 부여한다.
- 실행 projection 모델의 evidence 계약을 `citationKey` 중심으로 변경한다.
- 서버가 citation key를 원본 `SourceCitation`으로 역조회하고 locator, quote, extractionVersion, provenance를 서버 값으로 채운다.
- 존재하지 않는 citation key는 명확한 검증 오류로 차단한다.
- 기존 exact citation 검증과 provenance 보존을 회귀 테스트로 유지한다.

## 제외 범위

- 임베딩 모델, Python 전처리기, PGVector 검색 알고리즘 변경
- fuzzy locator 매칭 또는 잘린 chunk ID 자동 복구
- 생성 모델 교체

## 완료 조건

- 모델이 page/chunk/quote를 변조해도 해당 문자열이 최종 source reference로 저장되지 않는다.
- 유효한 citation key는 서버 원본 citation으로 정확히 복원된다.
- 미등록 citation key는 계획 생성을 성공 처리하지 않는다.
- 기존의 문서 ID·버전·locator·quote·confidence 검증과 provenance가 유지된다.

## 검증

- 모험 계획 생성 gateway/application 테스트에서 citation key 해석과 미등록 key 차단을 검증한다.
- projection 응답의 변조된 page/chunk 문자열이 무시되고 서버 citation으로 대체되는 회귀 테스트를 추가한다.
- 관련 adventure-service 테스트와 `git diff --check`, graphify 갱신을 실행한다.
