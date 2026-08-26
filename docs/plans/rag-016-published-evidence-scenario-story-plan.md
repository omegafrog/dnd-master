# RAG-016: 공개된 RAG 근거를 시나리오와 모험 계획에 전파

- Issue: #192
- Parent: #189
- Status: planned
- Dependencies: RAG-015

## 구현 목적

시나리오 준비와 모험 스토리 계획이 임시 원문 미리보기나 과거 색인이 아니라, 공개된 RAG 결과의 안정적인 근거와 locator를 사용하게 한다.

## 구현 범위

- `CrossContextHttpScenarioSourceExcerptGateway`의 규칙서 원문 선행 900자 슬라이싱을 공개 RAG 검색/근거 조회로 교체한다.
- 스토리북과 규칙서 모두에 문서, 공개 추출 버전, 페이지/섹션 locator가 있는 표준 근거 계약을 사용한다.
- 시나리오 컴파일러, source validator, `AdventureStoryPlanApplicationService`가 공개 근거만 수락하고 결과 인용에 provenance를 보존하게 한다.
- 근거가 없거나 공개되지 않았으면 명확하게 차단하거나 사용자가 재시도할 수 있는 오류를 반환한다.

## 제외 범위

- 전처리 호출/후보 수명주기는 RAG-014, 벡터 공개는 RAG-015의 책임이다.
- 페이지 재검토 재시도와 개발 데이터 reset은 RAG-017의 책임이다.

## 완료 조건

- 시나리오와 모험 계획의 근거는 공개 문서 버전과 페이지/섹션 locator를 역추적할 수 있다.
- 임시 source preview, 미공개 후보, 과거 벡터는 생성 근거로 사용되지 않는다.
- 근거가 부족한 생성은 출처 없는 추측으로 진행되지 않는다.

## 검증

### 정책 단위 테스트

- `CrossContextHttpScenarioSourceExcerptGatewayTest`에서 규칙서/스토리북 조회가 동일한 공개 provenance 계약을 쓰는지 검증한다.
- `AdventureStoryPlanApplicationServiceTest`, `AdventureStoryPlanStageSourceValidatorTest`, 생성 gateway 테스트에서 미공개/무근거 입력 차단과 인용 보존을 검증한다.

### UI-엔터티 E2E
