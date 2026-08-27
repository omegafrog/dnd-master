# Grounded Combat and Fail-Closed GM Quality Plan Index

Parent issue: [#194](https://github.com/omegafrog/dnd-master/issues/194)

| Plan | Issue | Status | Depends on | Vertical outcome |
| --- | --- | --- | --- | --- |
| [RAG-020](rag-020-effective-gm-provider-identity.md) | [#195](https://github.com/omegafrog/dnd-master/issues/195) | `completed` | — | 요청한 공급자와 실제 호출한 공급자를 분리해 턴 전체에서 감사 가능한 실행 정체성을 보존한다. |
| [RAG-021](rag-021-strict-gm-candidate-lifecycle.md) | [#196](https://github.com/omegafrog/dnd-master/issues/196) | `completed` | RAG-020 | 의미 기본값 없이 GM 후보를 검증하고 한 번만 보정한 뒤 성공 또는 재시도 가능 실패로 원자적으로 종료한다. |
| [RAG-022](rag-022-bounded-evidence-and-claim-citations.md) | [#197](https://github.com/omegafrog/dnd-master/issues/197) | `completed` | RAG-021 | 현재 단계와 행동 의도에 맞는 최대 8개 근거만 전달하고 응답 주장과 인용의 관련성을 검증한다. |
| [RAG-023](rag-023-grounded-combat-skeleton.md) | [#198](https://github.com/omegafrog/dnd-master/issues/198) | `completed` | RAG-018 (completed) | 전투 단계가 근거 기반 Combat Skeleton을 갖춘 경우에만 모험 계획을 READY로 만든다. |
| [RAG-024](rag-024-dependency-aware-plan-repair.md) | [#199](https://github.com/omegafrog/dnd-master/issues/199) | `completed` | RAG-019 (completed), RAG-023 | blocker의 의존 필드를 함께 회귀 수정하고 전체 계획을 다시 검증한다. |
| [RAG-025](rag-025-lazy-tactical-preparation-state.md) | [#200](https://github.com/omegafrog/dnd-master/issues/200) | `ready-for-agent` | RAG-023, RAG-024 | 미래 전술 준비 의도와 현재 준비 작업·장면 상태를 합성하고 READY 전 맵 활성화를 차단한다. |
| [RAG-026](rag-026-five-turn-quality-golden-journey.md) | [#201](https://github.com/omegafrog/dnd-master/issues/201) | `planned` | RAG-020, RAG-021, RAG-022, RAG-023, RAG-024, RAG-025 | DB 초기화부터 새 발행·계획·모험 시작·5턴까지 실제 품질 게이트를 반복 실행한다. |

## Dependency decision

`RAG-020`, `RAG-021`, `RAG-022`, `RAG-023`, and `RAG-024` are completed. RAG-025 is `ready-for-agent`; RAG-026 remains `planned` until every listed dependency is `completed`.
