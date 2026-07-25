# ADR-002: 문서, 시나리오, AI 책임 분리

## 상태

Accepted

## 배경

현재 rule-knowledge-service는 문서 저장·추출·검색 기반을 가지며, adventure-service의 시나리오 준비는 단일 파일과 no-op adapter에 머문다. AI 결과가 원문이나 패키지를 직접 변경하면 근거 검증과 소유권 경계가 무너진다.

## 결정

- `rule-knowledge-service`가 Knowledge Document, Extraction Version, Source Span, Asset, 검색 인덱스를 소유한다.
- `adventure-service`가 Scenario Source Bundle, Resolution Unit, Override, Scenario Package, Runtime Binding을 소유한다.
- `ai-game-master-service`는 버전된 입력에 대한 후보와 제안만 반환하며 영속 상태를 직접 변경하지 않는다.
- 시나리오 컴파일을 위한 새 배포 서비스는 만들지 않고 `adventure-service` 내부 bounded context로 시작한다.
- 서비스 간 참조는 ID와 버전으로만 전달한다.

## 결과

- RULEBOOK과 STORYBOOK이 공통 문서 기반을 사용하면서 검색 범위와 Evidence 계약은 분리된다.
- AI 모델 교체가 도메인 저장 계약을 변경하지 않는다.
- adventure-service 내부 Scenario Preparation과 Adventure Runtime 패키지 경계를 강제해야 한다.
