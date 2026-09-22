# ADR-021: 세션 생성 전 시나리오 준비의 문서 검색 범위

## Status

Accepted

## Context

시나리오 준비는 Scenario Source Bundle의 선택 문서로 Scenario Package를 만드는 작업이다. 이 시점에는 Scenario Package와 Adventure Session이 아직 없다. 세션에 고정된 문서를 검색하는 계약은 두 식별자를 요구하므로 준비 작업이 이를 사용하려면 존재하지 않는 식별자를 대신 넣어야 한다.

## Decision

- 시나리오 준비에는 별도 내부 검색 계약을 사용한다. 준비 호출자는 저장된 Scenario Source Bundle에서 소유자와 선택한 원문 문서·추출 버전을 확인한다. 시나리오 내용 검색에는 STORYBOOK, 준비 중 규칙 절차 검색에는 선택된 RULEBOOK을 사용한다.
- Document Knowledge는 전달된 문서·버전의 발행 상태와 허가 범위를 검증한다. STORYBOOK은 해당 소유자의 문서, RULEBOOK은 공개된 공유 룰북이어야 한다. 기존 Dense/BM25/RRF 검색 구현을 공유한다.
- 준비 계약에는 세션 또는 Scenario Package 식별자를 요구하거나 대신 만들어 넣지 않는다. 두 대상이 생성된 뒤의 검색은 기존 세션 전용 계약과 Session Knowledge Set 범위를 유지한다.

## Consequences

- 준비와 세션 검색의 허가 범위를 각각 검증해야 하지만, 검색 알고리즘을 중복 구현하지 않는다.
- 시나리오 준비는 선택 문서 밖을 검색할 수 없고, 존재하지 않는 세션·패키지를 나타내는 식별자를 남기지 않는다.
