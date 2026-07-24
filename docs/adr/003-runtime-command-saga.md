# ADR-003: Runtime Command Saga로 분산 상태 조정

## 상태

Accepted

## 배경

주사위, 캐릭터, 전투 지도, 세션 상태는 서로 다른 서비스가 소유한다. Runtime GM이 직접 상태를 바꾸거나 Adventure가 모든 상태를 복제하면 기존 경계와 권위 모델이 깨진다.

## 결정

- 각 bounded context가 자기 상태의 정본을 유지한다.
- `adventure-service`가 Runtime Command의 수명주기와 세션 결과를 조정한다.
- 명령은 `sessionId`, `turnId`, `commandId`, expected version을 포함한다.
- 각 서비스는 commandId 기준 멱등 처리를 제공한다.
- 분산 트랜잭션 대신 재시도 가능한 Saga를 사용한다.
- 필수 상태 반영이 완료되기 전 성공 narration을 확정하지 않는다.
- Runtime GM은 Planning과 Finalization 단계에서 제안만 생성한다.

## 결과

- 일부 서비스 장애 시 명령을 안전하게 재개할 수 있다.
- 이미 생성된 주사위 결과는 삭제하지 않고 미적용 결과로 감사 가능하게 남긴다.
- 원격 상태 변경의 일시적 불일치를 Runtime Command 상태와 운영 지표로 관리해야 한다.
