# 039-1 계획 READY와 모험 시작 게이트 분리

- Status: `completed`
- Dependencies: none
- Product Spec: `docs/specs/product-spec.md`
- Architecture Spec: `docs/specs/architecture-spec.md`

## 구현 목적

모험 계획 검증과 projection이 성공하면 맵별 전술 장면 생성이 남아 있어도 계획을 READY로 확정하고 모험 시작을 허용한다.

## 범위

- 계획 생성에서 eager tactical-scene generation 제거
- 모험 시작의 전체 전술 장면 준비 게이트 제거
- 계획 상태·패키지 리비전·파티 일치 검증 유지

## 테스트 계약

- 전술 장면이 ABSENT인 맵 단계도 READY가 되는 단위 테스트
- READY plan으로 모험 시작이 허용되는 통합 테스트
- 검증·projection 실패는 READY가 되지 않는 회귀 테스트

## 완료 조건

- 계획 생성 중 전술 장면 AI 호출이 없다.
- 검증·projection 성공 후 `플레이 준비 완료`가 반환된다.
- READY plan으로 모험 시작 요청이 성공한다.
- 직접 맵 활성화의 기존 안전 검사는 유지된다.
