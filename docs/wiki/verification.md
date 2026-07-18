# 검증

이 ChangeSet의 검증은 도메인, 서비스, UI, runtime을 나눠 확인한다. Playwright는 fixture 기반 UI 검증이고, Java system-tests는 backend/domain E2E 검증이다.

## 검증 층위

| 층위 | 범위 | 비고 |
| --- | --- | --- |
| System tests | 16개 | backend/domain E2E 기준 |
| Focused tests | 2, 3, 6, 9, 16 | 특정 경계의 집중 검증 |
| Service integration test | 39 | 서비스 단위 통합 검증 |
| Playwright | 2 | fixture 기반 UI 검증 |
| Runtime smoke | 7개 서비스 | health, api-docs, swagger-ui 확인 |

## 결과 요약

- 전체 검증 명령 53/53이 exit 0으로 종료되었다.
- runtime 검증은 7개 서비스의 health, API 문서, Swagger UI를 확인했다.
- 서비스 계약과 UI 검증은 서로 다른 층위로 취급한다.

## 해석

- backend/domain E2E의 기준은 Java system-tests이다.
- Playwright는 화면과 fixture 상호작용을 확인하지만, 백엔드 전 구간 E2E로 보지 않는다.
- focused 테스트는 특정 실패 구간을 재검증하는 용도다.
- 서비스 통합 테스트는 개별 BC 계약과 런타임 연결을 확인하는 용도다.
