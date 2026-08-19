# 038-1 전술 장면 계획 도메인 모델과 검증

- Issue: [#174](https://github.com/omegafrog/dnd-master/issues/174)
- Parent: [#179](https://github.com/omegafrog/dnd-master/issues/179)
- 상태: `ready-for-agent`
- 의존성: 없음

## 구현 목적

모험 단계에 불변 전술 장면 상태와 배치 검증 규칙을 도입한다. 이 계획은 유효하지 않거나 근거를 표시하지 않은 전술 단계가 전술 맵으로 활성화되는 것을 막는 기반이다.

## 범위

- `AdventureStoryPlanStage`에 versioned `TacticalScenePlan` 스냅샷을 추가한다.
- `NormalizedCoordinate`, `TacticalPlacement`, `TacticalEnvironment`, `FogPlan`, `TacticalTrigger`, `PlacementGrounding`을 값 객체로 모델링한다.
- 전술 단계의 필수 범주, 명시적 빈 목록, 좌표 범위, 충돌·금지 영역·전이 참조, 근거/AI 보완 표기를 검증한다.
- 기존 `stages_json`은 전술 장면이 `ABSENT`인 과거 버전으로 역직렬화하고, 재생성 전 활성화를 허용하지 않는다.

## 비범위

- AI 후보 생성·재시도, 실제 Combat Map 생성, 트리거 실행은 이 계획에서 구현하지 않는다.

## 완료 기준

- 전술 단계는 모든 필수 범주가 값 또는 명시적 빈 목록으로 존재할 때만 유효하다.
- 좌표가 [0, 1] 범위를 벗어나거나 충돌·금지 영역·전이 참조가 무효이면 거부된다.
- 모든 배치·환경·트리거에는 원문 인용 또는 `AI_INFERENCE` 근거가 있다.
- 기존 플랜은 하위 호환되지만 새 전술 활성화 전에 재생성이 필요하다.

## 테스트 계약

- 정책 단위: 필수 범주, 빈 목록, 좌표 경계, 충돌, 금지 영역, 근거 우선, 트리거 참조를 각각 실패하는 테스트로 고정한다.
- 저장소 통합: 새 스냅샷 왕복과 이전 `stages_json` 역직렬화를 검증한다.
- UI → 엔티티 E2E: 준비된 전술 단계가 누락된 필수 범주 때문에 활성화 명령을 거부하는 흐름을 검증한다.
