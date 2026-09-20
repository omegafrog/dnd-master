# ADR-018: 사용자 PC 에이전트 연결 중계 서비스를 별도 배포한다

## Status

Accepted

## Context

AI Game Master는 서버의 RAG와 모험 상태로 완성한 프롬프트를 만든다. 기존에는 서버 로컬의 Codex app-server가 이를 실행한다. 배포 환경에서는 Solo Player의 PC에만 있는 Codex 로그인 정보를 사용해야 한다.

사용자 PC 에이전트의 장기 연결은 일반 AI Game Master 요청 처리와 다른 확장·장애·배포 특성을 가진다. 여러 연결 중계 인스턴스가 실행될 때, 어떤 인스턴스가 특정 Solo Player의 웹소켓 연결을 보유하는지도 찾아야 한다.

## Decision

- 사용자 PC 에이전트 연결 중계 서비스를 독립 Netty 배포 모듈로 둔다.
- 이 서비스는 새 Bounded Context가 아니다. AI Game Master를 지원하는 기술 전달 능력이다.
- AI Game Master는 완성 프롬프트와 실행 옵션을 인증된 내부 HTTP로 중계 서비스에 보낸다.
- Redis에는 Solo Player ID별 연결 보유 인스턴스·연결 ID·만료 임대만 저장한다. 프롬프트, 결과, RAG 자료, Codex 로그인 정보는 저장하지 않는다.
- 로드밸런서가 임의 중계 인스턴스에 전달한 요청은 Redis에서 연결 보유 인스턴스를 찾고, 그 인스턴스에 인증된 내부 HTTP로 직접 전달한다.
- 영속 작업 대기열과 자동 재시도는 도입하지 않는다. 연결·라우팅·실행 실패는 게임 상태를 바꾸지 않고 수동 재시도로 끝낸다.

## Consequences

- 장기 연결의 수평 확장과 장애 격리를 AI Game Master 배포와 분리한다.
- 내부 HTTP, Redis, 서비스 배포·관찰 비용이 추가된다.
- 중계 서비스 재시작이나 사용자 PC 연결 끊김 중의 결과는 유실될 수 있다. 사용자 PC 에이전트 재연결 뒤 사용자가 수동 재시도한다.
- 사용자 PC 에이전트는 `/ws/agent`에 Bearer 토큰으로 연결하고, identity-access가 확인한 Solo Player ID로 등록한다.
- 연결 위치는 Redis 임대로 저장하며 60초 만료·20초 주기 갱신을 사용한다. 새 연결은 기존 연결을 대체하고, 종료 시 연결 ID가 일치할 때만 임대를 삭제한다.
- 웹소켓 실행 메시지와 결과 메시지는 `RelayExecutionRequest`·`RelayExecutionResult` JSON 계약을 사용하고 `requestId`로 내부 HTTP 응답과 연결한다.
- pairing, 기기 키, 자동 연결 복구, 영속 실행 보존은 후속 결정이다.
