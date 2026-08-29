# GMQ-001 Prompt Registry And Baseline

- Issue: #231
- Parent Issue: #210
- Status: `completed`
- Dependencies: #220

## 구현 목적

GM 역할별 Prompt와 생성 설정을 버전형 artifact로 관리한다. 운영 baseline, Eval dataset split, model·prompt lineage를 고정해 이후 비교·rollback의 신뢰 가능한 출발점을 만든다.

## 구현 범위

- Planner/Writer/Judge/Verifier role별 `PromptVersion`, `PromptArtifact`, active baseline 모델.
- prompt content, output schema, context ordering, exemplar placement, model의 versioned 등록.
- train/dev/holdout version과 scene/adventure 단위 leakage 방지.
- runtime이 승인된 active role configuration만 조회하도록 연결.
- operator registry read entrypoint.

## Acceptance Criteria

- role별 prompt/model version과 parent version은 독립적이다.
- 운영 요청은 active·approved artifact만 사용한다.
- baseline의 dataset/Eval/model/prompt version을 재현한다.
- holdout을 train/dev/candidate 입력으로 쓰면 거부한다.

## Test Contract

- Policy unit: role isolation, immutable version, active baseline 단일성, split leakage 거부.
- Integration: registry persistence, active configuration read, legacy inline prompt fallback 차단.
- UI ~ entity E2E: operator registry entrypoint → baseline 등록/조회 → runtime role configuration 적용 → GM turn API 회귀.
