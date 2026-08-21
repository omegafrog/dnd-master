# 039-3 전술 장면 준비 작업 영속화와 브라우저 검증

- Status: `ready-for-agent`
- Dependencies: `039-2`
- Product Spec: `docs/specs/product-spec.md`
- Architecture Spec: `docs/specs/architecture-spec.md`

## 구현 목적

전술 장면 준비 중 사용자가 서비스를 종료해도 작업 상태를 잃지 않고, 재접속 시 기존 Shard CN 진행 상태를 이어서 보여주며 Potent Brew 실제 흐름으로 검증한다.

## 범위

- process-local job을 DB 기반 session/stage job으로 전환
- QUEUED·RUNNING·COMPLETE·FAILED_RETRYABLE와 시도 횟수·사유 저장
- 재접속 조회와 중복 실행 방지
- Potent Brew Playwright에 계획 확정·모험 시작·맵 진입·재접속 검증 추가
- 단계별 스크린샷 저장

## 테스트 계약

- 프로세스 재시작 뒤 작업 상태를 복원하는 persistence integration test
- 중복 요청이 하나의 작업만 사용하는 concurrency test
- 실제 브라우저의 Shard CN 진행·재접속·재시도·맵 활성화 검증

## 완료 조건

- 재접속 시 기존 작업을 이어서 조회한다.
- 동일 세션·단계에 중복 작업이 생성되지 않는다.
- Potent Brew 브라우저 테스트가 `플레이 준비 완료`와 모험 시작까지 통과한다.
- 전술 장면 생성은 현재 진입 단계에서만 발생한다.
