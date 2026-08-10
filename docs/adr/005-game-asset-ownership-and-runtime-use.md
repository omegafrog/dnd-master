# ADR-005: Game Asset 소유권과 런타임 사용 경계

## 상태

Accepted

## 배경

지도와 Player Handout은 일반 검색 텍스트가 아니다. 원본 파일, 시각 해석, 공간 또는 공개 메타데이터가 필요하며 모험 장면과 연결되어 게임 중 안전하게 표시되어야 한다. Combat Map은 이미 토큰, 시야, Fog of War 같은 런타임 상태를 소유하지만 원본 자산이나 시나리오 의미를 소유하지 않는다.

## 결정

- Document Knowledge가 불변 Extraction Version에 속한 원본 Game Asset, 파생 설명, Content Role, 구조 메타데이터와 Source Span을 소유한다.
- Scenario Preparation이 Scene과 Game Asset 또는 Map Region의 연결, Player Handout과 GM Material의 연결을 Scenario Package에 컴파일한다.
- Combat Map이 선택된 Map Asset/Region으로부터 생성된 세션별 전술 상태, 토큰, 시야, Fog of War를 소유한다.
- Adventure Runtime이 현재 장면과 공개 정책을 검증한 뒤 `show_asset`과 `show_map` 도구 행동을 조정한다.
- 서비스 간에는 Asset ID, Extraction Version, Region ID, Package Version만 전달한다. 원본 저장 위치는 외부에 노출하지 않는다.
- Map/Handout 접근의 정본 경로는 `Adventure → Scene → Asset/Region` 직접 참조다. embedding 검색은 발견과 컴파일 보조 수단이다.
- Player Handout 원본과 GM 전용 해답·트리거·실패 결과는 별도 projection으로 유지한다.

## 결과

- 지도 의미, 시나리오 연결, 런타임 지도 상태가 한 aggregate에 섞이지 않는다.
- Handout 표시 시 해답 누출을 구조적으로 차단할 수 있다.
- 새 자산 역할을 Document Knowledge 모델에 추가하고 Scenario Preparation 연결 정책을 확장할 수 있다.
- 자산 추출, 장면 연결, 런타임 표시 사이에 ID·버전 계약과 추가 저장소가 필요하다.
- Combat Map은 원본 자산 저장소가 되지 않는다.
