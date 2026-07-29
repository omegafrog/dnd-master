# 029-5 - Ollama·Postgres E2E

- Status: completed
- Issue: [#97](https://github.com/omegafrog/dnd-master/issues/97)
- Parent: [029](029-rulebook-progressive-embedding.md)
- Dependencies: [029-1](029-1-progressive-embedding.md), [029-2](029-2-checkpoint-resume.md), [029-3](029-3-lease-revision.md), [029-4](029-4-progress-api.md)

## Outcome

실제 179페이지 PDF를 Ollama와 PostgreSQL/pgvector로 upload → progress → failure → resume → READY → search까지 검증한다.

## Acceptance

- 실제 Ollama embedding 호출이 동작한다.
- Testcontainers PostgreSQL/pgvector가 production migration을 사용한다.
- 부분 저장, resume, no duplicate embedding, revision guard, 검색 회귀를 검증한다.

## Tests

- system E2E: 실제 Ollama + Postgres + UI/API → entity.
- 기존 pgvector/search/revision guard regression.

## Scope

system fixture, service configuration, real-provider test path, 179-page fixture 및 E2E assertions.
