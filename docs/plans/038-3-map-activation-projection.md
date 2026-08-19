# 038-3 전술 맵 활성화와 플레이어 비공개 투영

- Issue: [#176](https://github.com/omegafrog/dnd-master/issues/176)
- Parent: [#179](https://github.com/omegafrog/dnd-master/issues/179)
- 상태: `planned`
- 의존성: `038-1`, `038-2`

## 구현 목적

승인된 전술 장면 계획을 실제 격자 런타임 상태로 적용하면서, GM의 전체 계획과 Solo Player가 볼 수 있는 공개 상태를 확실히 분리한다.

## 범위

- 정규화 좌표를 현재 `MapDefinition`의 격자 좌표로 변환한다.
- 토큰·환경·상호작용·초기 안개를 `CombatMap`의 mutable state로 materialize한다.
- GM/internal projection과 player-safe projection을 API 경계에서 분리한다.

## 비범위

- 계획된 트리거의 평가·미래 단계 개정은 038-4에서 처리한다.

## 완료 기준

- 활성화는 `READY` 전술 장면에서만 가능하며 배치가 유효한 격자 위치로 변환된다.
- 플레이어 응답은 숨겨진 좌표, 숨은 토큰, 함정, 미발견 보상, 트리거와 근거를 포함하지 않는다.
- GM 상태는 materialized runtime state와 원 계획 근거를 추적할 수 있다.

## 테스트 계약

- 통합: 정규화 좌표 변환, 토큰·환경·안개 생성 및 충돌 실패를 검증한다.
- API: 동일 단계의 GM 및 player projection을 비교해 비공개 필드 부재를 검증한다.
- UI → 엔티티 E2E: 플레이어 화면에 숨겨진 전술 요소가 표시되지 않는 상태에서 전술 맵 활성화를 검증한다.
