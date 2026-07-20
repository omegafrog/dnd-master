# 프로젝트 위키

이 위키는 CHG-20260717-001의 확정 근거를 한국어로 정리한 안내문이다. 핵심 범위는 AI 보조 D&D 모험 플랫폼이며, 로그인, 룰북 RAG, 시나리오, 적용 룰셋, 캐릭터, AI GM 스트리밍, 주사위, 전투 맵, 저장·재개·삭제 흐름을 포함한다.

## 한눈에 보기

| 항목 | 내용 |
| --- | --- |
| 실행 환경 | Java 21, Spring Boot, Spring AI, PostgreSQL + pgvector |
| 통신 방식 | 내부 서비스 간 `internal_http`와 정적 계약 기반 연동 |
| 핵심 BC | Identity, Adventure, Rule Knowledge, Character Management, Dice Roll, Combat Map, AI Game Master |
| 핵심 검증 | Java system-tests, Failsafe focused tests, 서비스 통합 테스트, Playwright fixture, runtime health/api-docs/swagger-ui |

## 문서

- [사용자 흐름](./user-workflows.md)
- [도메인 아키텍처](./domain-architecture.md)
- [운영](./operations.md)
- [검증](./verification.md)
- [변경 이력](./change-history.md)
- [API](./api.md)

## 읽는 순서

1. 사용자 흐름으로 유스케이스와 화면/행위 범위를 확인한다.
2. 도메인 아키텍처로 BC, Aggregate, 서비스 경계를 확인한다.
3. 운영으로 실행 환경, 포트, 스크립트를 확인한다.
4. 검증으로 통과 기준과 테스트 층위를 확인한다.
5. 변경 이력으로 이번 ChangeSet의 범위와 크기를 확인한다.
6. API로 OpenAPI 계약과 Swagger 진입점을 확인한다.
