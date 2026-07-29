# 029 - 룰북 임베딩 비동기·점진 저장

- Status: approved
- Issue: [#94](https://github.com/omegafrog/dnd-master/issues/94)
- Product Spec: `docs/specs/product-spec.md`
- Architecture Spec: `docs/specs/architecture-spec.md`
- Slices: [029-1](029-1-progressive-embedding.md), [029-2](029-2-checkpoint-resume.md), [029-3](029-3-lease-revision.md), [029-4](029-4-progress-api.md), [029-5](029-5-ollama-postgres-e2e.md)

## Outcome

대형 룰북 인덱싱을 bounded embedding batch, persisted checkpoint, lease/revision guard, progress API, 실제 Ollama/Postgres E2E로 안전하게 처리한다.

## Acceptance

- 179페이지 PDF가 전체 청크 단일 호출에 묶이지 않는다.
- 처리 중 total/completed/error/lease 상태를 조회한다.
- 실패·worker 재시작 뒤 완료 청크를 재사용한다.
- stale worker와 이전 revision 결과를 거부한다.
- 실제 Ollama + PostgreSQL/pgvector 흐름이 통과한다.

## Dependency

`029-1 → 029-2 → 029-3 → 029-4 → 029-5`
