# Product Spec: 검증된 전처리 기반 RAG 적재

## 1. Problem and Context

현재 문서 업로드 후 RAG용 추출·청크·검색 인덱스를 만드는 제품 흐름은 새 레이아웃 인식 전처리기의 결과를 사용하지 않는다. 따라서 다단·표·제목 구조를 보존하도록 개선한 전처리 결과가 모험 자료 준비, RAG 검색, 모험 계획 생성에 반영되지 않는다. 개발 환경에는 기존 방식으로 만든 데이터가 있으므로 새 동작을 검증할 수 있는 깨끗한 데이터 기준도 필요하다.

## 2. Goals and Desired Outcomes

- PRG-001: 새로 업로드한 RULEBOOK과 STORYBOOK은 전처리 결과를 정본으로 RAG 검색 가능 상태가 된다.
- PRG-002: 전처리 결과의 페이지 구조, 근거 위치, 청크 식별자가 검색 결과와 후속 모험 계획 근거까지 유지된다.
- PRG-003: 검증되지 않았거나 검토가 필요한 문서는 RAG와 모험 계획 생성에 노출되지 않는다.
- PRG-004: 개발 전환 시 기존 개발 DB의 문서 처리·RAG 데이터를 폐기하고 새 흐름으로 다시 적재할 수 있다.

## 3. Users and Actors

- Solo Player: 룰북과 시나리오 자료를 업로드하고, 준비된 자료를 바탕으로 모험을 준비·진행한다.
- Document Knowledge: 업로드 문서를 처리하고 검색 가능한 근거를 제공하는 제품 경계다.
- Scenario Preparation: 검증된 STORYBOOK 근거를 사용해 시나리오 패키지와 모험 계획을 준비한다.
- AI Game Master: 검증된 세션 지식 근거만 사용해 후보와 서술을 만든다.

## 4. Ubiquitous Language and Terminology

- **Preprocessed Extraction Version**: 원본 문서의 페이지·블록·읽기 순서·표·제목 구조와 검증 결과를 보존한 버전형 추출 산출물이다.
- **RAG Publication**: 검증된 Preprocessed Extraction Version에서 만든 청크와 검색 인덱스를 세션 검색에 공개하는 행위다.
- **Publication Block**: 문서의 전처리 검증이 완료되지 않아 해당 버전을 RAG에 공개하지 않는 상태다.
- **Development RAG Reset**: 개발 DB에서 기존 Knowledge Document 처리 상태, 추출 버전, 청크, 임베딩 및 검색 인덱스를 제거하는 일회성 전환이다. 저장소의 `docs/` 파일이나 원본 개발 자료 파일을 삭제하지 않는다.

## 5. Core Use Cases

### UC-RAG-PRE-001: 새 문서의 전처리 기반 RAG 적재

1. Solo Player가 RULEBOOK 또는 STORYBOOK을 업로드한다.
2. 제품은 문서를 전처리하고 페이지별 구조와 검증 결과를 만든다.
3. 전체 추출 버전이 공개 조건을 충족하면 전처리 결과에서 청크와 검색 인덱스를 만든다.
4. 제품은 문서를 `INDEXED`로 표시하고, 이후 세션 RAG와 모험 계획 생성이 그 근거를 사용한다.

### UC-RAG-PRE-002: 레이아웃·추출 실패의 격리와 복구

1. 특정 페이지가 검증에 실패하거나 `NEEDS_REVIEW`가 되면 해당 페이지의 진단과 재시도 정보를 보존한다.
2. 다른 페이지 분석은 계속할 수 있다.
3. 하지만 실패 페이지가 포함된 추출 버전은 RAG에 공개하지 않는다.
4. 재시도 후 모든 공개 조건을 만족한 새 버전만 RAG에 공개한다.

### UC-RAG-PRE-003: 개발 RAG 데이터 초기화와 재적재

1. 개발 환경 운영자가 Development RAG Reset을 수행한다.
2. 기존 개발 DB의 문서 처리·추출·청크·임베딩·검색 데이터가 제거된다.
3. 원본 개발 자료를 다시 업로드하면 UC-RAG-PRE-001의 새 흐름으로 처리한다.

## 6. Business Rules and Invariants

- BR-RAG-PRE-001: RAG는 공개된 Preprocessed Extraction Version에서 유래한 청크만 검색한다.
- BR-RAG-PRE-002: 페이지 하나라도 `NEEDS_REVIEW`, 실패 또는 미검증이면 그 Extraction Version은 RAG Publication 대상이 아니다.
- BR-RAG-PRE-003: 페이지 실패는 다른 페이지의 분석을 중단시키지 않지만, 부분 결과를 검색용 정본으로 공개하지 않는다.
- BR-RAG-PRE-004: 검색 결과는 원본 문서, 추출 버전, 페이지 또는 구조 위치까지 추적 가능해야 한다.
- BR-RAG-PRE-005: 모험 계획과 AI Game Master는 세션에 고정된 문서 범위 안의 공개된 근거만 사용한다.
- BR-RAG-PRE-006: Development RAG Reset은 개발 DB 데이터에만 적용하며 저장소 문서·원본 자료 파일을 삭제하지 않는다.

## 7. States and State Transitions

`QUEUED → PROCESSING → VALIDATED → INDEXED`

- 검증 실패 또는 검토 필요: `PROCESSING → NEEDS_REVIEW`
- 복구 재시도: `NEEDS_REVIEW → PROCESSING`
- 복구 불가: `PROCESSING → FAILED`
- `INDEXED`는 전체 추출 버전이 검증·공개된 경우에만 가능하다.

## 8. Failures, Exceptions, and Boundary Conditions

- 지원하지 않는 레이아웃, OCR 실패, 표/읽기 순서 검증 실패는 `NEEDS_REVIEW` 또는 `FAILED`로 기록한다.
- 실패한 추출 버전의 기존·부분 청크는 검색 결과에 섞이면 안 된다.
- 재시도는 같은 원본 문서와 실패 페이지의 진단을 추적할 수 있어야 한다.
- 개발 DB 초기화는 운영 또는 사용자 자료에 적용하지 않는다.

## 9. Inputs and Outputs

- 입력: RULEBOOK/STORYBOOK 원본 PDF, 문서 유형, 세션 지식 집합, 검색 질의.
- 중간 산출물: 페이지별 전처리 결과, 구조화된 Source Span, 검증 진단, 버전형 청크.
- 출력: 근거 위치를 포함한 RAG 검색 결과, 시나리오 준비 근거, 모험 계획 근거.

## 10. Scope and Non-goals

- 범위: 업로드부터 RAG 공개, 세션 검색, 시나리오·모험 계획의 근거 사용까지의 연결.
- 범위: 개발 DB의 기존 RAG 데이터 초기화와 재적재.
- 비목표: 원본 시나리오가 제공하지 않는 다중 결말·NPC·전투를 AI가 발명하도록 만드는 것.
- 비목표: 저장소 `docs/` 파일이나 개발용 PDF 자산을 초기화하는 것.
- 비목표: PDF 이외 형식의 RAG 적재.

## 11. Priorities and Trade-offs

- 검색 가능성보다 근거의 추적 가능성과 공개 전 검증을 우선한다.
- 부분 페이지 처리는 진단과 복구를 위해 유지하지만, 부분 인덱싱은 허용하지 않는다.
- 개발 전환에서는 기존 데이터 호환성보다 깨끗한 재적재를 우선한다.

## 12. Success Conditions and Acceptance Criteria

- AC-RAG-PRE-001: 새 STORYBOOK PDF 업로드가 전처리 검증 완료 후에만 RAG 검색 결과를 반환한다.
- AC-RAG-PRE-002: 표·다단 PDF의 검색 근거가 올바른 페이지와 구조 위치를 가리킨다.
- AC-RAG-PRE-003: `NEEDS_REVIEW` 페이지가 있는 문서는 RAG·모험 계획 생성에 공개되지 않는다.
- AC-RAG-PRE-004: 재시도 성공으로 새 추출 버전이 공개되면 이전 미검증 결과가 검색되지 않는다.
- AC-RAG-PRE-005: 개발 DB 초기화 후 재업로드한 문서가 새 전처리 기반 RAG를 통해 모험 계획 생성 근거로 사용된다.
