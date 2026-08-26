# RAG-009: Regional Column Reading Order

페이지를 레이아웃 영역으로 나누고 영역별 열 후보·Column Profile·읽기 순서를 계산해 단일 열, 다열, 혼합 열과 spanning block을 원문 순서대로 투영한다.

Plan: `docs/plans/rag-009-regional-column-reading-order.md`

GitHub: https://github.com/omegafrog/dnd-master/issues/182

Depends on: RAG-008

## Scope

- LayoutRegion, ColumnHypothesis/Profile and ReadingOrderPlan
- region-based column inference and candidate scores
- spanning-block insertion and repeated header/footer handling
- removal of raw extractor order as visual-order fallback

## Acceptance

- single, two-column and `1 → 2 → 1` fixtures match gold order
- every confirmed block occurs exactly once
- ambiguous hypotheses remain blocked with diagnostics
- policy unit and process-CLI-to-entity e2e tests pass
