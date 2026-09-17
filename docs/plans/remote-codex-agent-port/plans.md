# 원격 Codex 사용자 PC 에이전트 실행 계획

## 계획 세트

- Parent: [#320](https://github.com/omegafrog/dnd-master/issues/320)
- base branch: `spec/remote-codex-agent-port`
- base SHA: `a882ccb0d573dfffbcc8335b26182f0b18a66c76`
- plan branch: `plan/remote-codex-agent-port`
- 상태 정본: GitHub Project `omegafrog/5`
- 구현 상태: 완료된 코드 기준으로 문서·다이어그램을 갱신함

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
- [UC-02 연결·실행 상태](../../specs/remote-codex-agent-port/diagrams/architecture/agent-connection.state.svg)
- [UC-02 연결 유지 활동](../../specs/remote-codex-agent-port/diagrams/product/UC-02.activity.svg)

## 현재 구현 검증 범위

- `/ws/agent` Bearer 인증과 identity-access 식별자 확인.
- Redis 연결 위치 임대 등록·교체·60초 만료·20초 갱신.
- A=C 로컬 전달, A≠C `/internal/owned-executions` 직접 전달.
- 웹소켓 `RelayExecutionRequest`와 `RelayExecutionResult`의 `requestId` 상관관계.
- 연결 없음·연결 끊김·시간 초과·전달 실패 분류와 민감 원문 비로그.

세부 구현 근거는 `product-spec.md` 11절과 `architecture-spec.md` 11절을 따른다.
