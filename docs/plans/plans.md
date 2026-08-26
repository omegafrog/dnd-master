# Validated Preprocessing Product RAG Plan Index

Parent issue: [#189](https://github.com/omegafrog/dnd-master/issues/189)

| Plan | Issue | Status | Depends on | Vertical outcome |
| --- | --- | --- | --- | --- |
| [RAG-014](rag-014-preprocessing-process-lifecycle.md) | [#190](https://github.com/omegafrog/dnd-master/issues/190) | completed | — | Python PDF 전처리 프로세스를 문서 후보/페이지 수명주기에 연결한다. |
| [RAG-015](rag-015-published-preprocessing-vector-publication.md) | [#191](https://github.com/omegafrog/dnd-master/issues/191) | completed | RAG-014 | 검증된 산출물만 provenance와 함께 pgvector에 공개한다. |
| [RAG-016](rag-016-published-evidence-scenario-story-plan.md) | [#192](https://github.com/omegafrog/dnd-master/issues/192) | completed | RAG-015 | 공개 RAG 근거를 시나리오와 모험 계획 인용으로 전파한다. |
| [RAG-017](rag-017-review-retry-development-rag-reset.md) | [#193](https://github.com/omegafrog/dnd-master/issues/193) | ready-for-agent | RAG-014, RAG-015 | 검토 페이지 재시도와 개발 전용 RAG DB reset을 제공한다. |

## Dependency decision

Dependency graph evaluated on 2026-08-26. RAG-014 and RAG-015 were completed, enabling RAG-016 and RAG-017; RAG-016 is now completed and RAG-017 remains `ready-for-agent`.
