# 037-2 카탈로그 API와 리비전 고정

Issue: [#170](https://github.com/omegafrog/dnd-master/issues/170)
Parent: [#168](https://github.com/omegafrog/dnd-master/issues/168)
Status: `planned`
Dependencies: 037-1

## 구현 목적

백엔드 API가 카탈로그의 전체 스키마와 선택지를 제공하고, 캐릭터 생성·검증·저장이 동일한 카탈로그 리비전을 사용하게 한다. 카탈로그가 변경되어도 이미 만든 캐릭터의 규칙 해석이 바뀌지 않도록 버전을 고정한다.

## 구현 범위

- 기존 character rules catalog endpoint의 전체 schema projection 확장
- catalog revision을 bootstrap/evaluate/mutation 경계에 전파
- character build persistence에 revision reference 저장
- validator/mutation rules의 병렬 상수 제거 또는 catalog identifier 사용
- revision mismatch와 immutable revision precondition

## 의존성과 변경 경계

- 037-1의 loader와 resource contract를 사용한다.
- 모험 준비 endpoint의 base schema 연결은 037-3에서 수행한다.
- 프런트 API client 변경은 037-4에서 수행한다.
- 기존 캐릭터 데이터의 안전한 기본 revision 호환 정책을 명시한다.

## 테스트 계약

- API가 모든 선택지·설명·근거·revision을 반환하는 controller contract test
- revision mismatch, unknown choice, historical build validation unit test
- 실제 backend bootstrap/evaluate를 거치는 `ui ~ entity` E2E

## 완료 조건

- API revision이 하드코딩 `1`이 아니다.
- 캐릭터 생성과 검증이 동일 revision을 요구한다.
- 저장된 캐릭터가 catalog revision을 보존한다.
- 과거 데이터가 명시된 호환 정책으로 계속 읽힌다.
