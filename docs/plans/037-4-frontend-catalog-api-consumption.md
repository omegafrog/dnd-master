# 037-4 프런트 API 단일화

Issue: [#172](https://github.com/omegafrog/dnd-master/issues/172)
Parent: [#168](https://github.com/omegafrog/dnd-master/issues/168)
Status: `planned`
Dependencies: 037-3

## 구현 목적

프런트엔드가 룰북 선택지와 설명을 자체 보관하지 않고 백엔드 API의 catalog projection만 사용하게 한다. 기본 스키마 검토와 실제 캐릭터 입력이 같은 revision을 표시하며, 자동 계산 항목은 선택지로 잘못 노출하지 않게 한다.

## 구현 범위

- `Dnd5eCharacterCatalog`, `Dnd5eSubclassCatalog` 등 권위성 정적 catalog 제거
- typed API client와 revision-aware view model
- 검토 페이지의 계층형 기본 스키마/선택지 표시
- 캐릭터 생성 화면의 API 기반 race/class/background/equipment/spell 선택
- API 오류·revision mismatch·빈 선택지 상태

## 의존성과 변경 경계

- 037-3의 preparation contract를 소비한다.
- 캐릭터 계산 로직의 권위는 백엔드에 두며 프런트는 입력과 표시만 담당한다.
- 시각적 재설계는 필요한 범위의 접근성·접기 동작으로 제한한다.

## 테스트 계약

- 정적 catalog를 사용하지 않는 API client/view model unit test
- API response의 선택지·자동 계산 분류·근거 표시 component test
- 실제 백엔드와 연결된 캐릭터 생성 `ui ~ entity` Playwright E2E

## 완료 조건

- 프런트 소스에 판본별 권위 선택지 목록이 남아 있지 않다.
- 검토 페이지와 캐릭터 생성 페이지가 동일한 catalog revision을 사용한다.
- 모든 선택 항목은 API가 제공한 선택지를 보여준다.
