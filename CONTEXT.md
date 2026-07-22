# Ubiquitous Language

- **Knowledge Document**: 소유자가 업로드하는 RAG 원본 파일. 하나의 통합 업로드·처리 흐름을 사용한다.
- **Document Type**: Knowledge Document의 의미 메타데이터. 초기 값은 `RULEBOOK`과 `STORYBOOK`이다. 저장·검색·근거 표시에 사용한다.
- **Batch Upload**: 여러 Knowledge Document를 한 요청으로 접수하는 행위. 각 파일은 별도 처리 상태·실패 사유·재시도 단위를 가진다.
- **Session Knowledge Set**: 특정 모험 세션에 고정하는 Knowledge Document 목록. 해당 세션 RAG 검색은 이 목록만 대상으로 한다.
- **Query Intent Priority**: GM agent가 질의를 룰 또는 스토리 관련으로 판별해, Session Knowledge Set 안에서 해당 Document Type의 검색 결과에 우선순위를 부여하는 정책.
- **Session Knowledge Set Ownership**: Session Knowledge Set은 `SessionId`에 직접 연결한다. RAG는 세션에서 해석한 문서 ID 목록으로 검색 대상을 제한한다.
- **KnowledgeDocument Boundary**: `rule-knowledge-service`의 내부·공개 계약은 `KnowledgeDocument`와 `KnowledgeDocumentId`를 사용한다. `DocumentType`은 등록, 청크, 근거까지 유지한다.
- **Document Processing**: Batch Upload는 파일 저장·문서 등록 후 즉시 반환한다. 문서별 비동기 작업이 추출, 청킹, 임베딩, 인덱싱을 수행하며 상태는 `QUEUED`, `PROCESSING`, `INDEXED`, `FAILED`다. 재시도는 실패 문서 하나만 대상으로 한다.
- **Query Intent Classification**: GM agent가 질문을 `RULE`, `STORY`, `MIXED`, `UNKNOWN`으로 분류한다. adventure-service는 세션 문서 범위와 의도를 rule-knowledge-service에 전달하고, rule-knowledge-service는 범위 필터와 유형 재랭킹만 수행한다.
