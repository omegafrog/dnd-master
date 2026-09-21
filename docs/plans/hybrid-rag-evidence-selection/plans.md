# 복합 검색과 상황별 최소 충분 근거 선택 계획

## 추적 정본

- 부모 Issue: [#336](https://github.com/omegafrog/dnd-master/issues/336)
- 상태 정본: GitHub Project 5의 `Workflow Status`
- 시작 상태: 부모와 모든 하위 Issue `Planned`
- 세션 기준 브랜치: `spec/hybrid-rag-rrf-rerank`
- 고정 기준 커밋: `a4c1a1ace6654138cdfc995d8f25cb4276dd6694`
- 계획 브랜치: `plan/hybrid-rag-evidence-selection`

## 실행 순서

1. [#337 복합 검색 색인과 통합 후보 API](https://github.com/omegafrog/dnd-master/issues/337)
   - Dense·BM25 색인과 RRF 후보 API를 Document Knowledge 안에서 원자적으로 제공한다.
   - 의존성: 없음
2. [#338 관련도 재정렬과 상황별 근거 충분성 정책](https://github.com/omegafrog/dnd-master/issues/338)
   - AI 제안과 Adventure 검증을 연결하고 네 상황별 정책과 추가 검색 흐름을 일괄 전환한다.
   - 의존성: #337
3. [#339 오프라인 품질 평가와 CI](https://github.com/omegafrog/dnd-master/issues/339)
   - 검색·재정렬·근거 선택 지표를 고정 평가 데이터와 CI에서 재현한다.
   - 의존성: #338
4. [#340 실제 모험 E2E와 기준값 보정](https://github.com/omegafrog/dnd-master/issues/340)
   - 실제 모험 시나리오로 품질·지연을 측정하고 시간 제한과 새 지표 차단값의 후속 결정을 준비한다.
   - 의존성: #339

## 관련 명세

- [Product Spec](../../specs/hybrid-rag-evidence-selection/product-spec.md)
- [Architecture Spec](../../specs/hybrid-rag-evidence-selection/architecture-spec.md)

## 다이어그램

### Product

- [UC-HR-001 유스케이스](../../specs/hybrid-rag-evidence-selection/diagrams/product/UC-HR-001.usecase.svg)
- [UC-HR-001 액티비티](../../specs/hybrid-rag-evidence-selection/diagrams/product/UC-HR-001.activity.svg)
- [UC-HR-003 유스케이스](../../specs/hybrid-rag-evidence-selection/diagrams/product/UC-HR-003.usecase.svg)
- [UC-HR-003 액티비티](../../specs/hybrid-rag-evidence-selection/diagrams/product/UC-HR-003.activity.svg)

### Architecture

- [Document Knowledge 클래스](../../specs/hybrid-rag-evidence-selection/diagrams/architecture/document-knowledge.class.svg)
- [근거 선택 클래스](../../specs/hybrid-rag-evidence-selection/diagrams/architecture/evidence-selection.class.svg)

## 검증 계약

- 각 하위 Issue 본문이 구현 목적, 범위, 수용 기준, 정책 단위 테스트, `ui ~ entity` E2E를 소유한다.
- #339는 Recall@5 개선과 MRR·근거 회수율·NDCG@5 비회귀를 검증한다.
- #340은 구현 후 실제 값을 측정해 시간 제한과 새 지표 차단값을 사용자와 후속 확정한다.
- 이 문서는 탐색용 인덱스이며 상태 변경은 GitHub Project에서만 수행한다.
