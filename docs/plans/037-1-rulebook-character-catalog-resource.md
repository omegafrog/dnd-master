# 037-1 룰북 캐릭터 카탈로그 리소스

Issue: [#169](https://github.com/omegafrog/dnd-master/issues/169)
Parent: [#168](https://github.com/omegafrog/dnd-master/issues/168)
Status: `ready-for-agent`
Dependencies: 없음

## 구현 목적

룰북의 캐릭터 생성 기본 스키마와 선택지를 Java/TypeScript 코드에 중복 보관하지 않고, 백엔드가 소유하는 검증 가능한 버전 리소스로 만든다. 검토 화면과 캐릭터 생성 화면이 같은 데이터에 의존할 수 있는 기반을 제공한다.

## 구현 범위

- D&D 5e(2014) 캐릭터 카탈로그 JSON 리소스
- 필드, 전체 선택지, 설명, 자동 계산 여부, 룰북 출처·근거
- typed resource loader와 catalog validation
- 카탈로그 edition/revision 식별자
- 기존 정적 Java 데이터의 리소스 전환 seam

## 의존성과 변경 경계

- `character-management-service`가 리소스와 로더의 소유자가 된다.
- 룰북 문서 revision metadata는 `rule-knowledge-service` 소유로 유지한다.
- API endpoint 확장과 저장 리비전 전파는 037-2에서 수행한다.
- 프런트 정적 catalog 제거는 037-4에서 수행한다.
- XML 파서는 추가하지 않는다. JSON을 기본 포맷으로 사용한다.

## 테스트 계약

- 리소스 schema/edition/revision/필드/선택지/source reference 검증 단위 테스트
- 잘못된 리소스와 중복 선택지 로딩 실패 테스트
- 카탈로그 loader에서 entity/API projection까지 전달하는 `ui ~ entity` 계약 테스트

## 완료 조건

- 지원 판본 리소스가 백엔드 classpath에서 로드된다.
- 선택 가능한 항목과 자동 계산 항목이 데이터상 구분된다.
- 모든 선택지에 안정적인 식별자, 라벨, 출처 근거가 있다.
- 리소스 오류는 게시 가능한 catalog로 노출되지 않는다.
