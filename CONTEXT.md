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
- **Scenario Source Bundle**: 하나의 시나리오로 함께 분석할 원본 문서와 자산의 입력 묶음. 의미 해석 결과나 런타임 구조를 포함하지 않는다.
- **Scenario Package**: Scenario Source Bundle을 분석하고 컴파일해 만든 버전형 산출물. Source Span 검색 자료, Resolution Unit, 원문 참조, 추출 경고, 사용자 수정, 컴파일 보고서를 포함한다. 자체로 실제 플레이 가능성을 보증하지 않는다.
- **Runtime Binding**: 특정 Scenario Package 버전을 실제 GM agent, 게임 엔진, 룰북 지식 집합, 캐릭터, 도구와 결합한 세션 실행 구성.
- **Compilation Status**: 원본 자료의 추출, 구조화, 근거 연결, 컴파일 완전성을 나타내는 상태. 실제 플레이 가능성과 구분한다.
- **Playability Status**: Runtime Binding을 대상으로 프리플라이트한 결과. 현재 GM agent와 게임 엔진 구성으로 핵심 흐름을 실행할 수 있는지를 나타낸다.
- **Runtime Health**: 실제 세션 중 검색 실패, 상태 충돌, 시나리오 이탈 등 실행 품질을 나타내는 상태.
- **Source Span**: 원본 문서의 텍스트나 시각 요소와 그 위치를 보존하는 추적 단위. PDF와 이미지는 페이지·좌표·읽기 순서, DOCX와 TXT는 구조·문자 범위를 사용한다. 시나리오 컴파일 전 과정의 정본이다.
- **Progressive Scenario Compilation**: Source Span을 정본으로 유지하면서 안전하게 해석할 수 있는 판정과 굴림만 Resolution Unit으로 투영하는 방식. 구조화하지 못한 내용은 버리거나 추측하지 않고 원문 조회로 강등한다.
- **Resolution Unit**: 시나리오 원문에 명시된 판정 또는 굴림 절차를 실행 가능한 형태로 투영한 단위. 능력치·기술 판정, 내성, 공격, 피해, 회복, 대항, 우선권, 충전, 랜덤 테이블, 특수 굴림, 수동 수치 기준을 포함한다. 원문에 없는 절차나 결과는 생성하지 않는다.
