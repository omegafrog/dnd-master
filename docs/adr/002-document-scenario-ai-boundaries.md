# ADR-002: 문서, 시나리오, AI 책임 분리

## 상태

Accepted

Amended: 2026-08-05

## 배경

현재 rule-knowledge-service는 문서 저장·추출·검색 기반을 가지며, adventure-service의 시나리오 준비는 단일 파일과 no-op adapter에 머문다. AI 결과가 원문이나 패키지를 직접 변경하면 근거 검증과 소유권 경계가 무너진다.

## 결정

- `rule-knowledge-service`가 Knowledge Document, Extraction Version, Source Span, Asset, 검색 인덱스를 소유한다.
- `adventure-service`가 Scenario Source Bundle, Resolution Unit, Override, Scenario Package, Runtime Binding을 소유한다.
- `ai-game-master-service`는 버전된 입력에 대한 후보와 제안을 만들며 영속 저장소를 직접 변경하지 않는다.
- AI Game Master에는 범용 HTTP, DB, 파일, 셸 도구를 제공하지 않는다. 세션·턴 범위의 허용 목록형 도메인 도구만 제공한다.
- 쓰기 도구는 소유 백엔드의 Command API를 호출한다. 모든 명령은 `sessionId`, `turnId`, `commandId`, expected version을 포함하며 소유권·규칙 검증과 멱등 처리를 통과해야 한다.
- 도구 호출로 생성된 변경은 Runtime Command Saga의 pending 결과다. `adventure-service`가 모든 필수 결과를 검증하고 GM Turn을 확정하기 전에는 플레이어에게 성공 상태로 공개하지 않는다.
- 시나리오 컴파일을 위한 새 배포 서비스는 만들지 않고 `adventure-service` 내부 bounded context로 시작한다.
- 서비스 간 참조는 ID와 버전으로만 전달한다.

## 결과

- RULEBOOK과 STORYBOOK이 공통 문서 기반을 사용하면서 검색 범위와 Evidence 계약은 분리된다.
- AI 모델 교체가 도메인 저장 계약을 변경하지 않는다.
- AI 도구 호출은 도메인 API를 우회하지 못하고 각 bounded context의 데이터 소유권을 유지한다.
- adventure-service 내부 Scenario Preparation과 Adventure Runtime 패키지 경계를 강제해야 한다.
