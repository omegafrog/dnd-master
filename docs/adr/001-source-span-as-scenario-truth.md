# ADR-001: Source Span을 시나리오 원문 정본으로 사용

## 상태

Accepted

## 배경

자유 형식 D&D 시나리오의 모든 서술, 개체, 관계, 진행 방식을 공통 스키마로 완전하게 구조화할 수 없다. 구조화 결과를 정본으로 사용하면 원문 손실과 추론 오염이 발생한다.

## 결정

- 불변 Extraction Version에 속한 Source Span을 시나리오 원문 정본으로 사용한다.
- 사전 구조화 범위는 원문에 명시된 판정과 굴림을 나타내는 Resolution Unit으로 제한한다.
- Resolution Unit은 Source Span을 대체하지 않는 버전형 투영이다.
- 구조화하지 못하거나 검증하지 못한 내용은 Source Span 검색으로 강등한다.
- Scenario Package는 정확한 Document와 Extraction Version을 고정한다.

## 결과

- 모든 런타임 근거를 원문 위치까지 추적할 수 있다.
- 추출기와 모델을 변경해도 과거 Package와 세션을 재현할 수 있다.
- 임의 시나리오의 완전 컴파일을 보장하지 않는다.
- 원문 검색 품질과 Source Span 위치 정확도가 핵심 품질 속성이 된다.
