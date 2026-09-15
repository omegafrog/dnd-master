---
plan_id: 323
orchestration_state: implementing
attempt: 1
last_completed_step: "3차 리뷰 지적 수정과 재검증, graphify update 완료"
changed_files:
  - docs/plans/.runtime/323/checkpoint.md
  - src/settings.gradle.kts
  - src/architecture-tests/src/test/java/com/dndmaster/architecture/BuildModulesTest.java
  - src/architecture-tests/src/test/java/com/dndmaster/architecture/DomainResponsibilityContractTest.java
  - src/agent-connection-relay-service/
  - src/ai-game-master-service/src/main/java/com/dndmaster/aigamemaster/configuration/AiExecutionConfiguration.java
  - src/ai-game-master-service/src/main/java/com/dndmaster/aigamemaster/infrastructure/ai/RemoteAiExecutionPort.java
  - src/ai-game-master-service/src/main/resources/application.yml
  - src/ai-game-master-service/src/test/java/com/dndmaster/aigamemaster/infrastructure/ai/RemoteAiExecutionPortTest.java
  - .github/workflows/dnd-master-ci.yml
tests:
  - "PASS ./gradlew :agent-connection-relay-service:cleanTest :agent-connection-relay-service:test :agent-connection-relay-service:bootJar"
  - "PASS ./gradlew :architecture-tests:test --tests com.dndmaster.architecture.BuildModulesTest"
  - "PASS Redis 7.4 Testcontainers TTL 및 연결 ID 조건부 삭제 통합 테스트"
  - "PASS 인증 API → Redis 조회 → A!=C 직접 내부 HTTP → 최종 결과/연결 없음 통합 계약"
  - "PASS :ai-game-master-service:test 전체"
  - "PASS 이전 연결 조건부 TTL 갱신 거절, 전송 실패·연결 끊김 요청 대기 정리 회귀 테스트"
  - "PASS 단일 마감 시각 전파, 중계 간 전용 토큰, 연결 끊김 오류 보존, CI 모듈 등록"
  - "LOAD 200 동시 요청, 65538 UTF-8 bytes, p95 28.736 ms, 126480681 bytes/s, heap/direct delta 0"
  - "BASELINE FAIL 전체 check: 기존 Adventure 도메인 허용 목록 2건과 TransactionBoundaryIntegrationTest 1건"
blocker: null
smart_zone: "dispatch fits; 순차 구현 유지"
next_action: "3차 리뷰 수정 커밋 후 고정 기준 대비 독립 Standards/Spec 재리뷰 실행"
handoff_reason: null
updated_at: "2026-09-15T15:57:26+09:00"
---
