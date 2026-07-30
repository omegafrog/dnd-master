# Product Spec: 룰북 임베딩 비동기·점진 저장

## 1. Problem and Context

대형 룰북을 인덱싱할 때 원문 청크 전체의 임베딩이 끝날 때까지 결과가 저장되지 않는다. 약 179페이지 문서에서는 긴 단일 처리로 인해 UI와 저장 상태가 `EMBEDDING`, 완료 청크 0개로 오래 남는다.

이 때문에 사용자는 처리 진행을 확인할 수 없고, worker lease 만료나 재처리 여부를 판단하기 어렵다. 중간 실패가 발생하면 이미 완료한 청크도 잃고 처음부터 다시 처리해야 한다.

## 2. Goals and Desired Outcomes

- G-1. 대형 룰북 임베딩을 장시간 단일 처리로 묶지 않는다.
- G-2. 처리 중 완료 청크 수와 작업 진행 상태를 조회할 수 있게 한다.
- G-3. 임베딩 완료 단위마다 결과를 점진적으로 반영한다.
- G-4. 중간 실패 후 완료된 청크를 재사용하고 실패 지점부터 재개한다.
- G-5. worker 재시작과 lease 만료 뒤에도 작업을 안전하게 복구한다.
- G-6. 동시 실행 방지와 revision guard를 유지한다.
- G-7. 실제 대형 PDF 룰북 인덱싱 흐름을 검증한다.

## 3. Users and Actors

- A-1. 룰북 업로더: 인덱싱 상태, 진행률, 오류를 확인하고 재시도를 요청한다.
- A-2. 인덱싱 작업 관리자: 작업을 단위별로 가져와 실행하고 완료·실패·재개 상태를 갱신한다.
- A-3. Document Knowledge: 원문 청크, 임베딩, 인덱스 상태를 관리한다.
- A-4. Embedding Provider: 청크 텍스트를 임베딩 벡터로 변환한다.
- A-5. 운영자: lease 만료, 반복 실패, 마지막 오류를 진단한다.

## 4. Ubiquitous Language and Terminology

- **Indexing Job**: 하나의 문서 revision을 검색 가능한 상태로 만드는 작업.
- **Chunk**: 문서 원문을 검색·임베딩하는 처리 단위.
- **Embedding Batch**: 한 번의 임베딩 요청으로 처리하는 제한된 청크 묶음.
- **Completed Chunk**: 임베딩 결과와 필요한 메타데이터가 저장된 청크.
- **Checkpoint**: 완료된 처리 위치와 진행 수를 나타내는 작업 상태.
- **Lease**: 한 worker가 작업을 처리할 권한을 일정 시간 독점하는 계약.
- **Revision Guard**: 오래된 작업이 새 문서 revision이나 재처리 결과를 덮어쓰지 못하게 하는 조건.
- **Resumable Retry**: 완료된 청크를 재사용하고 미완료 단위부터 이어가는 재시도.

## 5. Core Use Cases

### UC-1. 대형 룰북 인덱싱 시작

1. 사용자가 문서 revision의 인덱싱을 요청한다.
2. 시스템은 전체 청크 수를 기준으로 작업을 생성하고 처리 상태를 노출한다.
3. 작업 관리자는 처리 가능한 작업을 lease와 함께 확보한다.
4. 작업은 제한된 임베딩 단위로 진행된다.

### UC-2. 임베딩 단위 완료 반영

1. worker가 미완료 청크의 다음 임베딩 단위를 선택한다.
2. Embedding Provider에 해당 단위를 요청한다.
3. 성공한 결과를 저장한다.
4. 완료 수, 진행 상태, 마지막 처리 시점을 갱신한다.
5. 남은 단위가 있으면 작업을 계속하거나 다음 처리 기회로 넘긴다.

### UC-3. 진행 상태 조회

1. 사용자 또는 운영자가 문서 revision의 인덱싱 상태를 조회한다.
2. 시스템은 전체 청크 수, 완료 청크 수, 상태, 마지막 오류, lease 정보를 반환한다.
3. 진행 중인 작업은 부분 완료 상태를 보여준다.

### UC-4. 실패 작업 재개

1. 임베딩 단위 또는 worker가 실패한다.
2. 시스템은 오류와 재시도 가능한 상태를 기록한다.
3. 재시작 또는 재시도 시 완료된 청크는 다시 임베딩하지 않는다.
4. 미완료 또는 실패한 단위부터 작업을 재개한다.

### UC-5. lease 만료 후 복구

1. worker가 lease를 갱신하지 못하거나 중단된다.
2. 작업은 다른 worker가 다시 처리할 수 있는 상태가 된다.
3. 새 worker는 현재 checkpoint를 기준으로 작업을 확보한다.
4. 이전 worker의 늦은 결과는 lease와 revision 조건을 통과하지 못하면 반영하지 않는다.

## 6. Business Rules and Invariants

- BR-1. 인덱싱 작업은 전체 청크 임베딩 완료 전에도 부분 결과를 저장해야 한다.
- BR-2. 한 번의 Embedding Provider 요청에는 제한된 수의 청크만 포함한다.
- BR-3. 성공적으로 저장된 청크는 재시도 시 중복 임베딩하지 않는다.
- BR-4. 완료 청크 수는 실제 저장 성공한 청크만 센다.
- BR-5. 작업 상태는 전체 청크 수와 완료 청크 수를 함께 제공해야 한다.
- BR-6. 실패 시 마지막 오류와 재개 가능한 지점을 보존한다.
- BR-7. 동시에 같은 문서 revision의 인덱싱 작업을 처리하지 않는다.
- BR-8. lease를 잃은 worker는 작업 결과를 반영할 수 없다.
- BR-9. 오래된 revision의 결과는 현재 대상 revision을 변경할 수 없다.
- BR-10. 이미 완료된 청크와 동일한 결과를 재저장해도 검색 인덱스가 중복 생성되지 않는다.
- BR-11. 영구 실패와 재시도 가능 실패를 구분해 사용자에게 노출한다.
- BR-12. 기존 검색 결과와 revision guard의 의미를 변경하지 않는다.

## 7. States and State Transitions

### Indexing Job State

`QUEUED → EMBEDDING → COMPLETED`

실패 시 `EMBEDDING → RETRYABLE_FAILURE` 또는 `PERMANENT_FAILURE`로 전환한다.

`RETRYABLE_FAILURE → EMBEDDING`은 checkpoint를 유지한 채 재개한다.

lease 만료 시 작업은 `EMBEDDING`에서 재확보 가능한 상태가 되며, 새 worker가 checkpoint 이후를 처리한다.

### Chunk State

`PENDING → EMBEDDING → COMPLETED`

처리 오류 시 `EMBEDDING → PENDING` 또는 재시도 대기 상태로 돌아간다. `COMPLETED` 청크는 재임베딩 대상이 아니다.

## 8. Failures, Exceptions, and Boundary Conditions

- F-1. Embedding Provider 요청 timeout/오류: 현재 단위만 실패시키고 오류를 기록한다.
- F-2. worker 중단: lease 만료 후 다른 worker가 checkpoint에서 재개한다.
- F-3. lease를 잃은 늦은 응답: 저장하지 않고 작업을 재확보한 worker 결과를 우선한다.
- F-4. 프로세스가 저장 직후 중단: 재시도는 저장된 청크를 재사용해야 한다.
- F-5. 일부 청크만 Provider에서 반환: 반환된 결과의 유효성 검증 후 안전한 결과만 반영한다.
- F-6. 문서 revision 변경: 이전 작업 결과를 새 revision에 섞지 않는다.
- F-7. 반복 실패: 정해진 정책에 따라 영구 실패로 전환하고 원인을 노출한다.
- F-8. 전체 청크 수 0 또는 원문 추출 실패: 임베딩을 시작하지 않고 명확한 실패 상태를 제공한다.

## 9. Inputs and Outputs

### Input

- 문서 ID와 대상 revision
- 인덱싱 작업 요청
- 문서 청크와 청크 식별자
- Embedding Provider 요청·응답
- worker lease와 lease token
- 재시도 요청

### Output

- 인덱싱 작업 상태
- 전체 청크 수와 완료 청크 수
- 완료된 임베딩 청크
- 진행률 또는 부분 완료 정보
- 마지막 오류와 재시도 가능 여부
- lease 보유자, 만료 시각, 갱신 상태
- 완료·실패·재개 결과

## 10. Scope and Non-goals

### In Scope

- 룰북 청크 임베딩의 제한된 처리 단위
- 단위 완료별 청크·진행 상태 저장
- checkpoint 기반 재개와 중복 임베딩 방지
- worker lease 만료 및 재시작 복구
- 동시 실행 방지와 revision guard
- 진행 상태 조회에 필요한 상태 정보
- 실제 대형 PDF + Ollama + Postgres 인덱싱 검증

### Non-goals

- 임베딩 모델 또는 검색 알고리즘 자체 변경
- 룰북 청킹 의미·크기 정책의 제품 변경
- 기존 검색 품질을 바꾸는 재랭킹 정책
- 임베딩 Provider를 Ollama 외 시스템으로 교체
- 사용자에게 작업 취소 기능 제공

## 11. Priorities and Trade-offs

- P0: 점진 저장, 재개 가능성, 중복 방지, lease/revision 안전성
- P1: 진행 상태와 오류의 조회·진단 정보
- P2: 실제 대형 PDF E2E와 운영 관측성 강화

처리량보다 부분 결과의 안전한 저장과 복구 가능성을 우선한다. 단위 크기와 동시성은 Provider 안정성, lease 시간, 저장 비용을 고려해 Architecture Spec에서 결정한다.

## 12. Success Conditions and Acceptance Criteria

- AC-1. 179페이지 룰북이 전체 청크 단일 동기 호출에 묶이지 않는다.
- AC-2. 처리 중 조회 시 완료 청크 수와 전체 청크 수가 실제 저장 상태와 일치한다.
- AC-3. 한 단위 완료 후 해당 청크와 진행 상태가 저장된다. 전체 완료 전 기존에 게시된 검색 인덱스의 안정성은 유지한다.
- AC-4. 중간 실패 후 재시작해도 완료된 청크를 중복 임베딩하지 않는다.
- AC-5. worker 재시작과 lease 만료 뒤 작업이 checkpoint에서 안전하게 재개된다.
- AC-6. lease를 잃은 worker의 늦은 결과가 현재 작업을 오염시키지 않는다.
- AC-7. 동시 worker가 동일 문서 revision을 중복 처리하지 않는다.
- AC-8. revision 변경 후 이전 작업의 결과가 새 revision에 저장되지 않는다.
- AC-9. 실제 Ollama + Postgres 환경에서 원본 대형 PDF 인덱싱 E2E가 통과한다.
- AC-10. 기존 검색 품질과 revision guard 회귀 테스트가 통과한다.
