# Validated Preprocessing Product RAG Plan Index

Parent issue: [#189](https://github.com/omegafrog/dnd-master/issues/189)

| Plan | Issue | Status | Depends on | Vertical outcome |
| --- | --- | --- | --- | --- |
| [RAG-014](rag-014-preprocessing-process-lifecycle.md) | [#190](https://github.com/omegafrog/dnd-master/issues/190) | completed | — | Python PDF 전처리 프로세스를 문서 후보/페이지 수명주기에 연결한다. |
| [RAG-015](rag-015-published-preprocessing-vector-publication.md) | [#191](https://github.com/omegafrog/dnd-master/issues/191) | completed | RAG-014 | 검증된 산출물만 provenance와 함께 pgvector에 공개한다. |
| [RAG-016](rag-016-published-evidence-scenario-story-plan.md) | [#192](https://github.com/omegafrog/dnd-master/issues/192) | completed | RAG-015 | 공개 RAG 근거를 시나리오와 모험 계획 인용으로 전파한다. |
| [RAG-017](rag-017-review-retry-development-rag-reset.md) | [#193](https://github.com/omegafrog/dnd-master/issues/193) | completed | RAG-014, RAG-015 | 검토 페이지 재시도와 개발 전용 RAG DB reset을 제공한다. |
| [RAG-018](rag-018-server-owned-citation-keys.md) | — | in-progress | RAG-016 | 모델이 복사한 인용 대신 서버 정본 citation key를 최종 근거로 사용한다. |
| [RAG-019](rag-019-bounded-projection-repair.md) | — | planned | RAG-018 | 구조화 projection 위반을 bounded repair·재생성·정직한 실패로 처리한다. |

## Dependency decision

Dependency graph evaluated on 2026-08-26. RAG-014 and RAG-015 were completed, enabling RAG-016 and RAG-017; RAG-018 implementation is committed but remains in-progress pending review follow-up, so dependent RAG-019 is planned and not ready-for-agent.
