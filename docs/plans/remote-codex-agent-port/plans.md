# 원격 Codex 사용자 PC 에이전트 실행 계획

## 계획 세트

- Parent: [#320](https://github.com/omegafrog/dnd-master/issues/320)
- base branch: `spec/remote-codex-agent-port`
- base SHA: `a882ccb0d573dfffbcc8335b26182f0b18a66c76`
- plan branch: `plan/remote-codex-agent-port`
- 상태 정본: GitHub Project `omegafrog/5`

## 실행 순서

1. [#321 RT-01](https://github.com/omegafrog/dnd-master/issues/321) — 모험 세션 요청 잠금, Solo Player ID 내부 전달. 의존성 없음.
2. [#322 AI-02](https://github.com/omegafrog/dnd-master/issues/322) — 공통 AI 실행 포트, 모든 직접 Codex 경로 수렴, 개발 전용 모듈. #321 뒤.
3. [#323 RELAY-03](https://github.com/omegafrog/dnd-master/issues/323) — Netty 중계 모듈, Redis 연결 위치 임대, A→C 내부 전달, 계측. #322 뒤.

## 관련 명세

- [Product Spec](../../specs/remote-codex-agent-port/product-spec.md)
- [Architecture Spec](../../specs/remote-codex-agent-port/architecture-spec.md)

## 다이어그램

- [UC-01 유스케이스](../../specs/remote-codex-agent-port/diagrams/product/UC-01.usecase.svg)
- [UC-01 활동](../../specs/remote-codex-agent-port/diagrams/product/UC-01.activity.svg)
- [UC-01 AI 실행 구조](../../specs/remote-codex-agent-port/diagrams/architecture/ai-execution.class.svg)
