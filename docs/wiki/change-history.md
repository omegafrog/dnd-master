# 변경 이력

이 ChangeSet은 42개 커밋과 436개 파일 변경 범위로 정리되었다. 허용 범위는 `dnd-master/**`와 `.github/workflows/dnd-master-ci.yml`에 한정되며, wiki 문서는 그 검증 결과를 읽기 좋게 요약한 것이다.

## 범위

- 대상 저장소: `dnd-master`
- 허용 변경 범위: `dnd-master/**`
- CI 보강 범위: `.github/workflows/dnd-master-ci.yml`

## 변경 성격

- 런타임 검증과 CI 재현성 보강
- UI 정적 분석과 테스트 경계 복구
- Failsafe와 Playwright 검증 연결 정리
- OpenAPI 계약과 실행 경로의 분리 정리

## 읽을 때 주의할 점

- 변경 이력은 구현 세부를 나열하는 기록이 아니라, 이번 ChangeSet이 어디까지를 포괄하는지 확인하는 용도다.
- 계약, runtime, UI 검증은 서로 다른 층위이므로 하나의 결과로 합쳐서 읽지 않는다.
- 범위 밖 파일이나 비밀값, 원시 로그는 이 위키에 포함하지 않는다.
