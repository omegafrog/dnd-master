# RAG-015: 검증된 전처리 산출물을 pgvector에 공개

- Issue: #191
- Parent: #189
- Status: completed
- Dependencies: RAG-014

## 구현 목적

검증 완료된 전처리 산출물만 구조화 청크와 출처 좌표를 보존한 채 pgvector에 색인하고, 검색은 항상 현재 공개 버전만 반환하도록 만든다.

## 구현 범위

- `rag_extraction_version`, `rag_extraction_page`, 공개 청크/벡터의 영속 모델과 migration을 추가한다.
- 전처리 산출물의 섹션 경로, 페이지, bbox/표 셀 등 원본 locator를 청크와 벡터 메타데이터에 보존한다.
- 모든 페이지가 검증된 후보만 트랜잭션으로 벡터를 생성하고, 성공한 뒤에 공개 버전 포인터를 전환한다.
- `PostgresRulebookIndexRepository` 및 검색 포트를 공개 버전/문서 식별자/locator provenance 기준으로 바꾼다.
- 버전 전환 중 부분 색인, 과거 후보, 검토 중 후보가 검색에 섞이지 않게 한다.

## 제외 범위

- 전처리 프로세스 시작과 페이지 수명주기는 RAG-014에서 제공한다.
- 시나리오 및 스토리 계획에서의 인용 사용은 RAG-016에서 제공한다.
- 재검토 재시도와 개발 데이터 초기화는 RAG-017에서 제공한다.

## 완료 조건

- 검증 완료 후보의 청크가 정확한 문서/추출 버전/페이지 locator를 지닌 벡터로 공개된다.
- 검토 필요 페이지가 있거나 벡터 생성이 실패하면 공개 포인터와 검색 결과는 변하지 않는다.
- 새 공개 버전으로 전환한 뒤 검색은 새 버전만 반환한다.

## 검증

### 정책 단위 테스트

- migration 및 repository가 추출 버전, 페이지, locator를 무손실로 저장하는지 검증한다.
- 페이지 미검증/벡터 생성 실패 시 부분 공개가 없고, 공개 전환이 원자적인지 `RulebookIndexingApplicationServiceTest`, `RulebookPgvectorIntegrationTest`로 검증한다.
- 검색 필터가 문서 및 공개 버전 provenance를 강제하는지 검증한다.

### UI-엔터티 E2E
