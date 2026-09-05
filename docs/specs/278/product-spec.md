# Product Spec: Combat Map Preparation and Runtime Visibility

## 1. Problem and Context

이 스펙은 이슈 #278의 전투 맵 표시·준비 문제와, 맵에 도달했을 때 플레이어의 초기 위치를 결정해야 한다는 요구를 다룬다.

현재 플레이어가 전투 맵을 사용할 때 다음 문제가 발생한다.

- 원본 맵 이미지에 이미 격자가 있어도 게임 격자가 원본 격자에 맞지 않아 위치와 거리 표현이 어긋난다.
- 원본 맵에 격자가 없는 경우에도 플레이 가능한 수준의 일관된 격자가 필요하다.
- 맵 격자선이 시각적으로 과하게 강조되어 원본 맵을 가린다. 격자는 얇은 검정색 outline이어야 한다.
- 전장의 안개가 적용되지 않아 플레이어가 아직 보거나 탐험하지 않은 영역까지 처음부터 확인할 수 있다.
- 맵 진입 시 플레이어 토큰이 적절한 위치에 배치되지 않거나, 진입 맥락과 무관한 위치에서 시작할 수 있다.
- 토큰 범례가 맵 사용을 보조하기보다 시각적 공간을 과도하게 사용한다.
- 원본 이미지의 큰 여백 때문에 실제 플레이 영역이 화면에서 작게 표시된다.

이 문제의 핵심은 맵 이미지를 그대로 보여 주는 것이 아니라, **실제 플레이 영역을 식별하고, 좌표계를 결정하고, 현재 진입 맥락에 맞춰 플레이어를 배치한 뒤, 플레이어가 알아야 하는 영역만 보여 주는 것**이다.

## 2. Goals and Desired Outcomes

- **G-1** 원본 맵의 플레이 영역을 불필요한 외곽 여백과 구분하여 실제 맵이 화면에서 충분한 크기로 보이게 한다.
- **G-2** 원본 맵에 신뢰할 수 있는 격자가 있으면 게임 격자가 그 격자와 정렬되게 한다.
- **G-3** 원본 맵에 신뢰할 수 있는 격자가 없으면 플레이에 적합한 정사각형 fallback 격자를 생성한다.
- **G-4** 플레이어에게 표시되는 격자선은 얇은 검정색 outline으로 표현하고 원본 맵을 시각적으로 압도하지 않게 한다.
- **G-5** 맵에 처음 진입할 때 플레이어 토큰을 시나리오의 진입 맥락 또는 맵의 유효한 진입 지점에 배치한다.
- **G-6** 플레이어 화면에서는 현재 보이지 않고 아직 탐험하지 않은 영역을 Fog of War로 숨긴다.
- **G-7** 플레이어가 이동하거나 시야 조건이 변하면 현재 시야와 이미 탐험한 영역이 구분되어 갱신된다.
- **G-8** 플레이어에게 허용되지 않은 위치의 적·NPC·오브젝트 정보는 Fog of War를 우회해 노출되지 않는다.
- **G-9** 토큰 범례는 전투 맵을 가리지 않으면서 현재 맵을 이해하는 데 필요한 정보를 간결하게 제공한다.
- **G-10** 동일한 맵과 동일한 입력 맥락에는 안정적이고 반복 가능한 맵 준비 결과를 제공한다.

## 3. Users and Actors

- **Solo Player**: 전투 맵을 보고 자신의 토큰을 조작하며, 탐험으로 공개되는 영역을 확인하는 유일한 인간 사용자다.
- **Adventure Runtime**: 현재 모험 위치와 맵 진입 맥락을 제공하고, 플레이어 행동에 따른 전투 맵 상태 변경을 조정한다.
- **Combat Map**: 지도, 격자, 토큰 위치, 이동 상태, 시야·탐험 상태를 소유하는 게임 기능이다.
- **AI Game Master**: 시나리오와 현재 상황을 근거로 전술 장면·배치·진입 위치 후보를 제안할 수 있지만 플레이어에게 숨겨진 상태를 직접 공개하지 않는다.
- **Scenario Source**: 맵 이미지와 시나리오의 위치·이동·진입 관계를 제공하는 원본 자료다.

## 4. Ubiquitous Language and Terminology

- **Map Source**: 전투 맵을 준비하는 데 사용하는 원본 이미지 또는 문서 내 맵 이미지.
- **Map Content Bounds**: 원본의 외곽 여백을 제외하고 실제 맵 콘텐츠가 차지하는 영역.
- **Printed Grid**: Map Source 이미지 자체에 이미 그려져 있는 반복 격자.
- **Grid Calibration**: 게임의 논리적 셀을 Map Content Bounds의 픽셀 위치와 맞추는 결과.
- **Generated Fallback Grid**: 신뢰할 수 있는 Printed Grid가 없을 때 게임이 생성하는 정사각형 논리 격자.
- **Grid Source**: 현재 격자가 `PRINTED`에서 정렬된 것인지 `FALLBACK`으로 생성된 것인지 구분하는 의미.
- **Spawn Anchor**: 특정 맵에 진입할 때 플레이어가 배치될 수 있는 유효한 진입 지점. 하나의 맵에 여러 Spawn Anchor가 있을 수 있다.
- **Active Spawn**: 현재 맵 진입 맥락을 기준으로 선택된 실제 플레이어 시작 위치.
- **Current Visibility**: 현재 플레이어가 직접 볼 수 있는 셀 집합.
- **Explored Area**: 과거에 공개되었지만 현재는 직접 보이지 않을 수 있는 셀 집합.
- **Fog of War**: 아직 공개되지 않은 맵 정보가 플레이어에게 보이지 않도록 하는 상태.
- **Token Legend**: 플레이어에게 보이는 토큰 기호와 의미를 설명하는 보조 UI.

## 5. Core Use Cases

### UC-1 업로드된 맵을 플레이 가능한 영역으로 준비한다

Map Source가 입력되면 실제 맵 콘텐츠와 외곽 여백을 구분한다. 확실하게 불필요한 외곽 여백은 제거하고, 실제 지도·격자·출입구 등 플레이에 필요한 콘텐츠는 유지한다.

준비 결과는 이후 격자 보정과 플레이 화면의 기준이 된다.

### UC-2 원본 맵의 Printed Grid를 사용한다

Map Content Bounds 안에서 신뢰할 수 있는 반복 격자가 확인되면 해당 Printed Grid를 게임 좌표계에 맞춘다.

플레이어에게 표시되는 게임 격자는 원본의 칸 경계와 일치해야 하며, 원본에 Printed Grid가 있다는 이유만으로 다른 임의 격자를 덧씌우지 않는다.

### UC-3 Printed Grid가 없는 맵에 fallback 격자를 만든다

신뢰할 수 있는 Printed Grid가 확인되지 않으면 게임은 맵의 크기와 형태를 기준으로 일관된 정사각형 격자를 만든다.

Printed Grid 탐지 실패와 fallback 격자 생성은 구분되는 결과이며, fallback을 마치 원본 격자를 발견한 것처럼 취급하지 않는다.

### UC-4 맵에 진입하고 플레이어를 배치한다

Adventure Runtime이 새 전투 맵을 활성화하면 현재 시나리오 위치와 이전 위치에서의 이동 관계를 기준으로 적합한 Spawn Anchor를 선택한다.

명시적인 진입 지점이 있으면 그것을 우선한다. 하나의 맵에 여러 출입구·계단·통로가 있을 수 있으므로 맵마다 하나의 고정 시작 위치만 가정하지 않는다.

명시적인 Spawn Anchor를 결정할 수 없는 경우에도 플레이 가능한 유효 위치를 선택하며, 맵 좌표의 임의 기본값을 의미 있는 시작 위치인 것처럼 사용하지 않는다.

### UC-5 Fog of War와 탐험 상태를 보여 준다

맵이 활성화되면 플레이어의 위치와 현재 시야에 따라 Current Visibility를 결정한다. 아직 공개되지 않은 셀은 보이지 않아야 한다.

플레이어가 이동하거나 문·장애물 등 시야 조건이 변하면 Current Visibility가 갱신된다. 이미 보았던 영역은 Explored Area로 유지할 수 있지만 현재 시야와 구분한다.

### UC-6 플레이어용 맵과 토큰 정보를 표시한다

플레이어 화면은 준비된 맵 콘텐츠, 정렬된 격자, 현재 허용된 토큰, Current Visibility, Explored Area를 함께 표현한다.

격자는 얇은 검정색 outline으로 표시한다. Token Legend는 현재 맵 이해에 필요한 토큰 의미를 간결하게 제공하며 맵 영역을 불필요하게 축소하거나 가리지 않는다.

## 6. Business Rules and Invariants

- **BR-1** 신뢰할 수 있는 Printed Grid가 있으면 그 격자가 Generated Fallback Grid보다 우선한다.
- **BR-2** Generated Fallback Grid는 신뢰할 수 있는 Printed Grid가 확인되지 않은 경우에만 사용한다.
- **BR-3** 여백 제거는 보수적으로 수행한다. 외곽 여백을 줄이기 위해 실제 플레이 영역, 외곽 격자선, 출입구 또는 맵 콘텐츠를 잘라내서는 안 된다.
- **BR-4** 게임 격자의 셀은 플레이 가능한 좌표계를 일관되게 표현해야 하고, 맵 경계 밖의 토큰 위치를 정상 상태로 허용하지 않는다.
- **BR-5** 플레이어에게 표시되는 격자선은 얇은 검정색 outline이어야 하며 황색 강조선으로 표시하지 않는다.
- **BR-6** Fog of War는 맵 이미지에 미리 구워진 정적 장식이 아니라 플레이 중 변하는 세션 상태다.
- **BR-7** Current Visibility에 포함된 셀은 현재 보이고, Explored Area이지만 Current Visibility가 아닌 셀은 과거에 본 영역으로 구분되며, 아직 탐험하지 않은 셀은 숨겨져야 한다.
- **BR-8** 현재 플레이어에게 공개되지 않은 토큰·오브젝트 정보는 맵 표시나 범례를 통해 우회 노출되어서는 안 된다.
- **BR-9** Active Spawn은 현재 진입 맥락과 연결된 Spawn Anchor를 우선하고, 선택된 위치는 유효한 게임 셀이어야 한다.
- **BR-10** 좌표 `(0,0)` 같은 임의 기본 좌표는 해당 위치가 실제로 선택된 유효 Spawn인 경우가 아니면 자동 시작 위치로 취급하지 않는다.
- **BR-11** 의미 있는 Spawn Anchor를 결정할 수 없을 때는 플레이 가능한 안전한 fallback 위치를 사용하되, 이를 의미 기반 Spawn으로 가장하지 않는다.
- **BR-12** 동일한 Map Source와 동일한 준비 조건은 입력이 바뀌지 않는 한 안정적인 Map Content Bounds와 Grid Calibration을 제공해야 한다.
- **BR-13** Token Legend는 플레이어에게 실제로 노출 가능한 토큰 의미만 설명해야 하며 숨겨진 토큰 존재 자체를 누설해서는 안 된다.

## 7. States and State Transitions

### 7.1 Map Preparation 상태

| 현재 상태 | 트리거 | 다음 상태 | 의미 |
|---|---|---|---|
| `SOURCE_RECEIVED` | Map Source 해석 성공 | `BOUNDS_NORMALIZED` | 실제 맵 콘텐츠 범위를 사용할 수 있다. |
| `BOUNDS_NORMALIZED` | Printed Grid 신뢰 가능 | `GRID_CALIBRATED` | Printed Grid를 좌표계로 사용한다. |
| `BOUNDS_NORMALIZED` | Printed Grid 없음 또는 신뢰 불가 | `GRID_CALIBRATED` | Generated Fallback Grid를 사용한다. |
| `GRID_CALIBRATED` | 플레이에 필요한 맵 정보 준비 완료 | `READY` | 맵을 활성화할 수 있다. |
| 어느 준비 상태 | Map Source를 해석할 수 없음 | `FAILED` | 해당 Source로 맵을 준비할 수 없다. |

`GRID_CALIBRATED` 상태는 `Grid Source = PRINTED | FALLBACK`을 구분한다.

### 7.2 Runtime 상태

| 현재 상태 | 트리거 | 다음 상태 | 결과 |
|---|---|---|---|
| `READY` | Adventure Runtime이 맵 진입을 요청 | `ACTIVE` | Active Spawn을 선택하고 플레이어를 배치한다. |
| `ACTIVE` | 플레이어 이동 또는 시야 조건 변경 | `ACTIVE` | Current Visibility와 Explored Area를 갱신한다. |
| `ACTIVE` | 다른 맵/장면으로 이동 | `READY` 또는 비활성 상태 | 현재 맵은 더 이상 플레이 화면의 활성 맵이 아니다. |

## 8. Failures, Exceptions, and Boundary Conditions

- Map Source가 손상되었거나 지원되는 맵 이미지로 해석할 수 없으면 맵 준비를 실패로 표시하고 전체 화면 공개 같은 임의 fallback을 사용하지 않는다.
- 외곽 여백과 실제 맵 콘텐츠의 경계가 불확실하면 과도하게 자르지 않고 원본에 가까운 보수적 범위를 유지한다.
- Printed Grid 후보가 불확실하거나 규칙적이지 않으면 잘못 정렬된 Printed Grid로 확정하지 않고 Generated Fallback Grid를 사용한다.
- 원본 맵에 장식선, 벽선, 텍스처가 많더라도 이를 격자로 오인해 플레이 좌표계를 왜곡해서는 안 된다.
- 시나리오에서 명시적인 Spawn Anchor를 결정할 수 없으면 유효한 fallback 위치를 선택한다. 맵 밖, 막힌 셀, 다른 필수 토큰과 충돌하는 셀은 시작 위치가 될 수 없다.
- Current Visibility 계산을 정상적으로 제공할 수 없는 경우 플레이어에게 전체 맵을 공개하는 방향으로 실패해서는 안 된다.
- 큰 맵 또는 매우 작은 맵에서도 격자가 플레이 가능한 수준으로 유지되어야 하며, 표시 크기 때문에 실제 맵 콘텐츠가 다시 과도하게 축소되어서는 안 된다.
- Token Legend에서 사용할 정보가 일부 없더라도 전투 맵 자체의 사용을 막지 않아야 한다.

## 9. Inputs and Outputs

### Inputs

- 필수: Map Source 또는 이미 준비된 맵 자산.
- 필수: 맵을 사용하는 Adventure 및 Solo Player 식별 정보.
- 선택: Map Source에 포함된 Printed Grid.
- 선택: 시나리오/전술 장면이 제공하는 플레이어·NPC·오브젝트 배치 정보.
- 선택: 이전 위치, 진입 방향, 출입구, 계단 등 Spawn Anchor를 선택하는 데 사용할 수 있는 진입 맥락.
- 선택: 벽, 문, 장애물 등 현재 시야 계산에 영향을 주는 맵 상태.

### Outputs

- 준비된 실제 맵 콘텐츠 영역.
- `PRINTED` 또는 `FALLBACK`으로 구분되는 게임 격자.
- 활성 맵의 플레이어 및 공개 가능한 토큰 위치.
- Current Visibility와 Explored Area.
- 플레이어에게 허용된 맵 레이어와 Token Legend.
- 맵 준비 또는 활성화가 실패한 경우 원인을 구분할 수 있는 실패 결과.

## 10. Scope and Non-goals

### In Scope

- 이슈 #278의 Printed Grid 정렬 문제.
- Printed Grid가 없는 맵의 fallback 격자.
- 외곽 여백을 줄여 실제 맵 영역을 크게 표시하는 동작.
- 얇은 검정색 격자 outline.
- 맵 진입 시 플레이어 토큰의 유효한 초기 배치.
- 현재 진입 맥락 또는 Spawn Anchor를 이용한 시작 위치 선택.
- 플레이어용 Fog of War, Current Visibility, Explored Area.
- 숨겨진 토큰 정보의 플레이어 화면 누출 방지.
- Token Legend의 간결한 플레이어용 표시.

### Non-goals

- 육각형·아이소메트릭 등 정사각형이 아닌 격자 시스템 지원.
- 모든 래스터 맵에서 벽·문·비밀문을 완전 자동 추출하는 기능.
- UVTT/DD2VTT 등 새로운 맵 파일 포맷 지원.
- 사용자가 직접 격자선과 crop 영역을 편집하는 전용 맵 편집기.
- 새로운 전투 시야 규칙 또는 룰 시스템 자체의 재설계.
- AI 생성 맵의 미술 스타일 또는 생성 품질 재설계.
- 브라우저에 전달된 원본 이미지 바이트를 악의적인 사용자가 직접 추출하지 못하게 하는 DRM 수준의 비밀 보호. 이번 범위의 Fog of War는 정상 플레이 UI에서의 정보 공개 제어를 의미한다.
- 다이어그램 파일 생성. 이번 작업에서는 사용자의 별도 스킬 호출로 분리한다.

## 11. Priorities and Trade-offs

우선순위는 다음과 같다.

1. **숨겨진 정보 누출 방지**: 모르는 영역을 과도하게 가리는 것은 수정 가능하지만, 아직 보지 못한 맵을 공개하면 플레이 경험을 되돌릴 수 없다.
2. **유효하고 맥락에 맞는 플레이어 시작 위치**: 잘못된 Spawn은 전투·탐험 진행 자체를 깨뜨린다.
3. **Printed Grid 정렬 정확성**: 원본 격자가 있는데 다른 격자를 덧씌우는 것보다, 불확실한 경우 fallback으로 명확히 전환하는 편을 우선한다.
4. **보수적인 여백 제거**: 조금의 여백을 남기는 것이 실제 맵 일부를 잘라내는 것보다 낫다.
5. **표시 품질**: 격자 색상과 Token Legend는 위의 게임 상태 정확성을 해치지 않는 범위에서 개선한다.

## 12. Success Conditions and Acceptance Criteria

- **AC-1** Printed Grid가 명확한 대표 맵에서는 게임 격자선이 원본의 반복 격자 경계와 눈에 띄는 어긋남 없이 정렬된다.
- **AC-2** Printed Grid가 없는 대표 맵에서는 `FALLBACK` 격자가 생성되고 정사각형 셀로 Map Content Bounds를 플레이 가능한 좌표계로 나눈다.
- **AC-3** 격자로 오인하기 쉬운 벽선·장식선만 있는 맵은 잘못된 `PRINTED` 격자로 확정되지 않는다.
- **AC-4** 큰 단색 또는 저정보 외곽 여백이 있는 대표 맵은 여백이 줄어들고, 가장 바깥쪽 실제 맵 콘텐츠와 격자선은 잘리지 않는다.
- **AC-5** 플레이어 화면의 격자선은 얇은 검정색 outline으로 표시되며 황색 격자선을 사용하지 않는다.
- **AC-6** 맵 활성화 시 플레이어 토큰이 유효한 셀에 존재한다. `(0,0)`은 실제 Active Spawn으로 선택된 경우가 아니면 암묵적 기본 위치로 사용되지 않는다.
- **AC-7** 진입 맥락과 일치하는 Spawn Anchor가 제공되면 해당 Anchor를 기준으로 플레이어가 배치된다.
- **AC-8** 첫 플레이어 맵 표시에서 Current Visibility 및 이미 허용된 Explored Area 밖의 영역은 Fog of War로 숨겨지고 전체 맵이 무조건 공개되지 않는다.
- **AC-9** 플레이어 이동 또는 시야 조건 변경 이후 Current Visibility는 새 위치에 맞게 바뀌고, 이전에 공개된 영역은 Explored Area 정책에 따라 유지된다.
- **AC-10** 현재 공개되지 않은 적·NPC·오브젝트는 맵 셀과 Token Legend를 통해 존재나 위치가 누출되지 않는다.
- **AC-11** Token Legend는 맵 영역을 가리지 않는 보조 UI로 표시되고, 플레이어에게 노출 가능한 토큰 의미만 중복 없이 설명한다.
- **AC-12** Map Source를 해석할 수 없는 경우 명시적인 준비 실패가 반환되고, 실패 때문에 플레이어에게 전체 맵을 공개하지 않는다.
- **AC-13** 동일한 Map Source와 동일한 준비 입력을 반복 처리하면 실질적으로 동일한 Map Content Bounds와 Grid Calibration 결과를 얻는다.

## Product 다이어그램 계약

이번 변경은 UC-1~UC-6의 흐름을 변경하므로 Product use-case/activity 다이어그램 대상이다.

별도 호출에서 생성할 다이어그램 계약은 다음과 같다.

| 대상 | 원본 경로 | 렌더링 SVG | 목적 |
|---|---|---|---|
| UC-1~UC-3 | `docs/specs/278/diagrams/product/UC-1-map-preparation.activity.puml` | [SVG](diagrams/product/UC-1-map-preparation.activity.svg) | crop, Printed Grid 판별, fallback 선택 흐름 |
| UC-4 | `docs/specs/278/diagrams/product/UC-4-map-activation.activity.puml` | [SVG](diagrams/product/UC-4-map-activation.activity.svg) | 진입 맥락과 Spawn 선택 흐름 |
| UC-5~UC-6 | `docs/specs/278/diagrams/product/UC-5-fog-visibility.activity.puml` | [SVG](diagrams/product/UC-5-fog-visibility.activity.svg) | 활성화·이동에 따른 visibility/explored 변화 |
| UC-1~UC-6 | `docs/specs/278/diagrams/product/UC-278-combat-map.usecase.puml` | [SVG](diagrams/product/UC-278-combat-map.usecase.svg) | Solo Player와 Combat Map 관련 유스케이스 범위 |

**상태:** READY — `.puml` 원본을 저장소의 고정 PlantUML renderer로 SVG에 렌더하고, 4개 SVG의 비어 있지 않음과 링크를 검증했다.
