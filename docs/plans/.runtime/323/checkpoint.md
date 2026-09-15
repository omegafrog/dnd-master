---
plan_id: 323
orchestration_state: implementing
attempt: 1
last_completed_step: "독립 리뷰 지적에 대한 연결 수명·현재 소유권·전송 경합·최종 요청 계측 보강과 전체 집중 검증, graphify update 완료"
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
fixed_point: c84a675b182eddbcab42fd695e6b316d317441e7
implementation_commits:
  - 45e3cac3
  - 44a1e3cf
  - 90940d43
  - 861591b3
  - 29343620
  - 7eee1661
  - 9b204f51
  - 2d54e580
tests:
  - "PASS ./gradlew :agent-connection-relay-service:cleanTest :agent-connection-relay-service:test :agent-connection-relay-service:bootJar"
  - "PASS ./gradlew :architecture-tests:test --tests com.dndmaster.architecture.BuildModulesTest"
  - "PASS Redis 7.4 Testcontainers TTL 및 연결 ID 조건부 삭제 통합 테스트"
  - "PASS 인증 API → Redis 조회 → A!=C 직접 내부 HTTP → 최종 결과/연결 없음 통합 계약"
  - "PASS :ai-game-master-service:test 전체"
  - "PASS 이전 연결 조건부 TTL 갱신 거절, 전송 실패·연결 끊김 요청 대기 정리 회귀 테스트"
  - "PASS 단일 마감 시각 전파, 중계 간 전용 토큰, 연결 끊김 오류 보존, CI 모듈 등록"
  - "PASS 만료 요청 즉시 TIMEOUT 계측, relay/AI 실행 메트릭 implementation 태그 회귀 테스트"
  - "PASS 중복 요청 선등록 거절, 전송 포함 마감, 교체 연결 보호, 프롬프트 원문 보존, 소유 인스턴스 계측 회귀 테스트"
  - "PASS Spec 리뷰 지적 5건 수정 후 relay cleanTest/test/bootJar, BuildModulesTest, ai-game-master 전체 테스트"
  - "PASS 완료 후 재등록 방지, 전송 중 연결 끊김, 응답 UTF-8 크기 제한, 연결 교체 요청 종료 회귀 테스트"
  - "PASS 연결 ID 대상 검증, 임대 완료 전 취소, JSON UTF-8 크기, 동기 전송 예외 정리 회귀 테스트"
  - "PASS 결과 수신 후 전송 중 연결 끊김 및 다른 인스턴스가 소유한 이전 로컬 연결 거절 회귀 테스트"
  - "PASS graphify update ."
  - "LOAD 200 동시 요청, 65538 UTF-8 bytes, p95 28.736 ms, 126480681 bytes/s, heap/direct delta 0"
  - "BASELINE FAIL 전체 check: 기존 Adventure 도메인 허용 목록 2건과 TransactionBoundaryIntegrationTest 1건"
blocker: null
smart_zone: "dispatch fits; 순차 구현 유지"
next_action: "현재 #323 수정 커밋 후 고정 기준 대비 독립 Standards/Spec 재리뷰 재실행"
handoff_reason: null
updated_at: "2026-09-15T17:32:00+09:00"
---
