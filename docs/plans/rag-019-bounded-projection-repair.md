# RAG-019: 구조화 projection 위반의 bounded repair와 정직한 실패

- Issue: —
- Parent: #189
- Status: ready-for-agent
- Dependencies: RAG-018

## 구현 목적

모험 계획 projection이 구조화 계약을 위반했을 때 후보 전체를 버리거나 검증기를 느슨하게 만들어 통과시키지 않고, 서버가 위반 내용을 구조적으로 기록한 뒤 복구 가능한 필드만 제한적으로 고친다. 이전 전체 후보와 정본 citation/map/source registry를 repair 경계에 함께 전달하고, repair 결과는 항상 전체 후보로 받아 완전한 결정론적 검증 체인을 다시 통과시킨다. 같은 후보의 반복 수정을 제한하고 무진전 반복과 근거 부족을 구분해, 복구할 수 없는 경우에는 추측 없이 정직하게 생성을 중단한다.

## 구현 범위

- projection 검증 위반을 구조화해 `code`, `stage position`, `field path`, `rejected value`, `citation context`, `repairability classification`을 포함하도록 한다.
- 거부된 projection candidate 전체를 보존하고, 진단·관측·repair 입력에서 참조할 수 있게 한다.
- 이전 전체 candidate, 위반 목록, authoritative citation registry, map registry, source registry만을 입력으로 받는 전용 bounded projection repair request를 정의한다.
- repair는 위반 목록에 열거된 필드만 수정하도록 제한하고, 결과는 부분 patch가 아닌 전체 candidate로 반환하게 한다.
- repair 결과에 대해 projection schema, citation/map/source 정본 일치, business/policy 규칙을 포함한 완전한 결정론적 validation chain을 처음부터 다시 실행한다.
- 각 위반을 `REPAIRABLE`, `REGENERATE_REQUIRED`, `SOURCE_EVIDENCE_INSUFFICIENT`, `SYSTEM_CONTRACT_ERROR` 중 하나로 분류한다.
- 동일 candidate에 대한 repair는 최대 2회로 제한하고, 이후 최대 1회의 전체 regeneration fallback만 허용한다. 전체 candidate 시도 횟수는 최초 후보를 포함해 5회를 넘지 않게 한다.
- 동일 입력·동일 위반에서 진전이 없는 반복을 감지해 즉시 중단하고, source evidence가 부족한 실패는 보강·추측·fuzzy matching 없이 `SOURCE_EVIDENCE_INSUFFICIENT`로 정직하게 종료한다.
- validator를 완화하거나 citation을 추론·fuzzy-match하지 않는다. authoritative registry에 없는 citation, map, source는 복구 성공으로 취급하지 않는다.
- repair/regeneration 시도 수, 단계별 위반 코드·분류, no-progress 중단, source-insufficient 종료, 최종 성공·실패를 기존 correlation context로 관측 가능하게 한다. 원문 후보와 민감한 근거 내용은 로그에 남기지 않는다.

## 제외 범위

- RAG-018의 server-owned citation key 발급·역조회 계약 자체 변경
- validator 규칙 완화, citation/map/source의 추론·fuzzy matching, 누락 근거의 생성
- 임베딩 모델, PDF 전처리기, 검색 알고리즘 또는 projection 외 생성 모델 교체
- 무제한 repair loop, 부분 candidate 저장, 검증을 생략한 repair 결과 공개

## 완료 조건

- 모든 projection 위반이 지정된 구조화 필드를 갖고, 거부된 전체 candidate가 보존된다.
- repair request가 이전 전체 candidate·위반·세 authoritative registry를 명시적으로 운반하고, repair 결과가 전체 candidate로만 반환된다.
- repair는 열거된 field path만 변경하며, 매 시도 후 완전한 결정론적 validation chain이 실행된다.
- 네 가지 repairability 분류와 분류별 전이·종료 동작이 테스트로 고정된다.
- 최대 2회의 동일-candidate repair, 1회의 regeneration fallback, 전체 5회 candidate-attempt 상한과 no-progress 반복 감지가 모두 강제된다.
- source evidence가 부족하거나 시스템 계약이 깨진 경우 성공으로 위장하지 않고 원인 분류와 함께 중단된다.
- repair 관측값이 운영 진단에 충분하고, 기존 정상 projection·citation 보존 흐름이 회귀하지 않는다.

## 검증

- 정책 기반 unit 테스트에서 위반 구조, rejected candidate 보존, field 제한, 전체 재검증, 네 분류, attempt budget, no-progress, source-insufficient 종료를 검증한다.
- application/gateway 회귀 테스트에서 RAG-018의 authoritative citation 해석과 결합해 repair가 등록되지 않은 citation·map·source를 통과시키지 않는지 검증한다.
- `ui ~ entity` live E2E에서 정상 후보, repair 성공, regeneration fallback, bounded failure, source-insufficient 정직한 실패와 관측 correlation을 확인한다.
- 관련 adventure-service 테스트, `git diff --check`, 문서 계획 검증 및 필요한 graphify 갱신을 실행한다.
