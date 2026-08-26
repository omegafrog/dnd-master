# RAG-014: 전처리 프로세스를 문서 수명주기에 연결

- Issue: #190
- Parent: #189
- Status: ready-for-agent
- Dependencies: none

## 구현 목적

PDF 규칙서와 스토리북을 등록할 때 기존 Docling 직접 추출 경로 대신, 검증 가능한 Python 전처리 프로세스를 호출한다. 페이지별 전처리 결과와 검토 상태를 문서 후보 버전에 보존하며, 검증되지 않은 후보는 이후 RAG 공개 단계로 넘어가지 못하게 한다.

## 구현 범위

- Java 문서 지식 컨텍스트에 `PreprocessingProcessPort`와 CLI adapter를 추가하고 `src/preprocessing_agent/adapters/process_cli.py`의 `preprocess`, `status`, `retry_pages` 계약을 호출한다.
- 규칙서/스토리북 등록 시 PDF만 이 RAG 경로의 입력으로 허용하고, 비 PDF는 명시적인 지원하지 않음 결과로 끝낸다.
- 후보 추출 버전과 페이지 상태를 영속화한다. 프로세스 상관관계 ID, 입력 해시, 버전, 산출물 매니페스트를 검증한 뒤에만 후보 결과를 수용한다.
- `NEEDS_REVIEW` 페이지와 페이지별 진단을 안전한 상태 조회 응답에 노출한다. 산출물 절대 경로나 내부 토큰은 노출하지 않는다.
- PDF에 대한 기존 `DoclingPdfRulebookContentExtractor` 직접/폴백 경로를 공개 가능한 콘텐츠의 출처로 사용하지 않게 한다.

## 제외 범위

- pgvector 공개 색인과 검색 전환은 RAG-015에서 한다.
- 시나리오와 모험 계획의 인용 전파는 RAG-016에서 한다.
- 검토 페이지 선택 재시도와 개발 DB 초기화 UX는 RAG-017에서 한다.

## 완료 조건

- 업로드가 실제 Python process port를 통해 후보 추출 버전과 페이지 상태를 만든다.
- 검토가 필요한 페이지가 하나라도 있으면 후보는 공개 가능한 상태가 아니다.
- 지원하지 않는 입력과 프로세스 응답 계약 위반은 기존/새 공개 버전을 오염시키지 않는다.
- 기존 PDF 직접 추출 폴백이 새 RAG 공개 경로에서 호출되지 않는다.

## 검증

### 정책 단위 테스트

- process CLI 요청/응답 상관관계, 해시, 버전, 매니페스트 검증 실패를 단위 테스트한다.
- PDF 외 입력 거절, `NEEDS_REVIEW` 후보 차단, 안전한 진단 직렬화를 `RulebookPipelineApplicationServiceTest` 및 controller 계약 테스트로 검증한다.
- 전처리 프로세스의 기존 `tests/integration/test_process_port.py`와 상태/재시도 계약 테스트를 유지한다.

### UI-엔터티 E2E
