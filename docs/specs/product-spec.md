# 전술 장면 지연 생성 Product Spec

## 1. Problem and Context
현재 모험 계획 생성은 모든 맵 단계의 전술 장면을 미리 AI로 생성한다. 이 때문에 계획 검증과 projection이 끝난 뒤에도 준비가 오래 걸리고 한 장면의 실패가 전체 실행 준비를 막는다.

## 2. Goals and Desired Outcomes
- 계획 검증과 projection이 끝나면 모험을 최종 실행 준비 상태로 확정한다.
- 전술 장면은 해당 맵 단계에 진입하기 직전에 준비한다.
- 전술 장면 실패가 이미 확정된 모험 계획을 무효화하지 않게 한다.
- 사용자가 생성 중 이탈해도 재진입 시 진행 상태를 이어간다.

## 3. Users and Actors
- Solo Player: 계획 생성·모험 시작·맵 단계 진입을 수행한다.
- AI Game Master: 계획 검증과 전술 장면 후보 생성을 수행한다.
- Adventure Runtime: 실행 준비 상태와 단계 진입을 관리한다.

## 4. Ubiquitous Language and Terminology
- Adventure Story Plan: 모험 단계·목표·전환·결말 계획.
- Tactical Scene: 특정 맵 단계의 배치·경계·상호작용 정보.
- Stage Entry: 현재 단계의 맵 또는 장면을 실제로 활성화하는 시점.
- Final Execution Ready: 계획이 확정되어 모험 시작을 허용하는 상태.

## 5. Core Use Cases
### UC-01 계획 생성 및 실행 준비
계획 생성, 검증, projection이 성공하면 전술 장면이 없어도 계획을 Final Execution Ready로 저장한다.

### UC-02 맵 단계 진입
맵이 필요한 현재 단계에 진입할 때 해당 단계 하나만 준비한다. 준비 중에는 Shard CN 프로그레스바를 표시하고 대기한다. 성공하면 맵을 활성화하고 실패하면 해당 단계의 진입을 막고 사유와 재시도 버튼을 제공한다.

### UC-03 작업 재개
사용자가 작업 중 이탈했다가 돌아오면 저장된 현재 단계 작업 상태를 조회해 기존 Shard CN 프로그레스바와 함께 중복 생성 없이 이어서 보여준다.

## 6. Business Rules and Invariants
- 검증 PASS와 projection 성공은 Final Execution Ready의 필수 조건이다.
- 전술 장면은 맵 단계 진입 전까지 계획 확정의 필수 조건이 아니다.
- 맵이 없는 단계는 전술 장면 준비 없이 진행한다.
- 같은 세션·단계에는 동시에 하나의 준비 작업만 존재한다.
- 전술 장면 실패는 해당 단계 진입을 막을 수 있지만 계획을 BLOCKED로 바꾸지 않는다.
- 모험 시작은 계획·패키지 리비전·파티 리비전이 일치할 때 허용한다.

## 7. States and State Transitions
계획: `GENERATING → VERIFYING → PROJECTED → FINAL_EXECUTION_READY`.
전술 장면: `ABSENT → PREPARING → READY` 또는 `FAILED_RETRYABLE`.

## 8. Failures, Exceptions, and Boundary Conditions
- 계획 검증·projection 실패는 위반 사유와 함께 실행 준비를 거부한다.
- 전술 장면 AI 실패는 해당 단계의 준비 작업만 실패·재시도 상태로 만든다.
- 최대 재시도 후에는 맵 진입을 막고 실패 사유와 재시도 동작을 제공한다.
- 사용자가 이탈해도 작업 상태는 유실되지 않는다.
- 중복 진입은 기존 준비 작업 상태를 반환한다.
- 미래 단계의 전술 장면은 미리 생성하지 않는다.

## 9. Inputs and Outputs
- 계획 생성 입력: 세션, 패키지 리비전, 파티, 설정, 문서·맵 컨텍스트.
- 계획 생성 출력: 확정된 계획과 실행 준비 상태.
- 전술 장면 입력: 현재 단계, 맵 정의, 근거, 파티.
- 전술 장면 출력: 준비 상태와 맵 활성화 정보 또는 재시도 사유.

## 10. Scope and Non-goals
포함: 전술 장면 생성 시점 분리, 단계 진입 준비, 재개 상태, 최종 실행 준비까지의 흐름.
제외: MCP 서버와 GM 도구 계약, 토큰 도구, 새로운 맵 권한 모델.

## 11. Priorities and Trade-offs
모험 시작까지의 응답성을 우선한다. 전술 장면 지연은 실제 맵 단계 진입 시점으로 이동한다.

## 12. Success Conditions and Acceptance Criteria
- Potent Brew 브라우저 흐름이 계획 확정과 모험 시작까지 도달한다.
- 계획 생성 구간에는 전술 장면 AI 호출이 없다.
- 맵 단계 진입 시 준비가 시작되고 성공하면 맵이 활성화된다.
- 전술 장면 실패 후에도 계획은 Final Execution Ready로 유지된다.
- 재접속 후 진행 중 작업을 조회하고 같은 단계에서 재개한다.
