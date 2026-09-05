# Architecture Spec: Combat Map Preparation and Runtime Visibility

# 1. Design Scope

## 1.1 Target

| 항목 | 대상 |
|---|---|
| Product Spec | `docs/specs/278/product-spec.md` |
| Use Cases | UC-1 ~ UC-6 |
| Domain | Combat Map preparation, activation, token placement, visibility projection |
| Bounded Contexts | 기존 `Combat Map`; 연동은 `Adventure Runtime`, `AI Game Master` |
| Existing Services | `combat-map-service`, `adventure-service`, `ai-game-master-service`, `web-ui` |
| External Dependencies | Java image/PDF decoding, PostgreSQL, existing HTTP contracts |
| Affected Data | CombatMap, GridSpec, MapLayer, token positions, VisibilitySnapshot, active-map relation |

현재 `CONTEXT-MAP.md`는 Combat Map을 `지도, 토큰, 이동 상태`를 소유하는 독립 Bounded Context로 두고 `combat-map-service`에 배치한다. 이번 변경의 crop, grid calibration, spawn resolution, fog projection은 이 소유권과 동일한 데이터 lifecycle을 사용하므로 **새 Bounded Context, 새 Gradle module, 새 배포 service를 만들지 않는다.** 가장 약한 충분 경계는 `combat-map-service` 내부 capability다.

현재 코드와 목표 설계의 핵심 차이는 다음과 같다.

- `MapGridDetector`가 Printed Grid 탐지 실패를 표현하지 못하고 `dimension / 20` fallback을 내부에서 생성하므로 `PRINTED`와 `FALLBACK`의 의미가 섞여 있다.
- 검출 결과가 32칸을 넘으면 20x20으로 축소하는 보정은 실제 Printed Grid를 발견한 경우에도 원본 격자를 버릴 수 있다.
- 업로드 맵 준비는 원본 이미지를 crop하지 않고 `GRID_BOUNDS`만 추가하므로 큰 외곽 여백 자체는 남아 있다.
- `CombatMapViewService.saveNew`는 PLAYER가 없으면 기본 `(0,0)`에 토큰을 생성하므로 맵 진입 맥락과 Spawn 결정이 분리되어 있지 않다.
- 현재 `VisibilityPolicy`는 blocker가 없으면 플레이어 위치에서 모든 셀까지 line-of-sight가 열려 전체 맵을 `current`에 넣을 수 있다.
- `PlayerSafeFogProjection`의 `INITIAL_FOG`는 추가 마스크일 뿐 일반적인 탐험 Fog를 만들지 못하며, 업로드 맵에는 이 레이어가 기본 생성되지 않는다.
- Web UI는 `current`/`explored`를 표현할 수 있지만 값이 없을 때 `visible = true`로 해석해 fail-open할 수 있다.

## 1.2 Product Spec Mapping

| Product Spec 항목 | Architecture 요소 |
|---|---|
| UC-1, BR-3 | `MapContentBoundsDetector` + `MapPreparationPipeline` |
| UC-2, BR-1 | `MapGridDetector`는 Printed Grid candidate만 반환, `PrintedGridAcceptancePolicy`가 채택 여부 결정 |
| UC-3, BR-2 | 독립 `FallbackGridPolicy` |
| UC-4, BR-9~BR-11 | `MapActivationContext` + `SpawnResolutionPolicy` + 원자적 map activation |
| UC-5, BR-6~BR-8 | `VisibilityPolicy` + `VisibilityProfile` + `VisibilitySnapshot` + `PlayerSafeFogProjection` |
| UC-6, BR-5, BR-13 | `PlayerCombatMapResponse`/`CombatMapView.tsx`/CSS player projection |
| 준비 실패 | 명시적 map-source error; grid ambiguity는 fallback으로 복구 |
| Spawn 실패 | `NO_VALID_PLAYER_SPAWN`, activation rollback |
| visibility 계산 실패 | player-origin-only fail-closed projection |

---

# 2. Domain Flow

## 2.1 Event Storming Flow

실제 다이어그램은 사용자 지시에 따라 별도 생성한다. 구현 계약 기준의 흐름은 다음과 같다.

### Map Preparation

`PrepareUploadedMap → Decode Source → Detect Content Bounds → Normalize Raster → Detect Printed Grid Candidate → Accept Printed Grid OR Build Fallback Grid → Build PreparedMapData → Persist CombatMap`

Map Preparation은 PLAYER의 실제 런타임 Spawn을 확정하지 않는다. Tactical scene이 명시적인 PLAYER placement를 제공한 경우 해당 placement는 activation candidate로 보존할 수 있지만, 맵 파일 자체를 처리했다는 이유만으로 `(0,0)` 토큰을 생성하지 않는다.

### Map Activation

`ActivateCombatMap → Load Prepared CombatMap → Resolve Spawn → Add/Move PLAYER Token → Refresh Visibility → Persist Map Version + Active Map Relation Atomically → Return Player Projection`

### Runtime Visibility

`MoveToken / ChangeDoor / Tactical State Change → Validate Command Version → Apply State Change → Refresh Visibility → Persist → Project current/explored/visible tokens`

## 2.2 Commands

| Command | Actor | Target | Input | Preconditions | Result |
|---|---|---|---|---|---|
| `PrepareUploadedMap` | Adventure Runtime / setup | Combat Map | owner, adventure, ruleset, map source | source decodable | prepared CombatMap |
| `PrepareTacticalMap` | AI Game Master via validated runtime flow | Combat Map | source/generation + tactical materialization | normalized placements valid | prepared CombatMap |
| `ActivateCombatMap` | Adventure Runtime | Combat Map | mapId, owner, expectedVersion, `MapActivationContext` | prepared map owned by player | player spawn + initial visibility + active map |
| `MoveToken` | Solo Player via Adventure Runtime | CombatMap aggregate | versioned move command | token controllable, path valid | moved token + refreshed visibility |
| `ChangeDoor` | authorized runtime command | CombatMap aggregate | door position/open state | map/version valid | door state + refreshed visibility |

## 2.3 Domain Events

이번 변경에 새로운 cross-service event publication은 필요하지 않다. 아래 이름은 설계·테스트 추적을 위한 **conceptual domain events**이며 기존 event infrastructure를 새로 만들지 않는다.

| Domain Event | Producer | Trigger | 주요 데이터 | Consumer |
|---|---|---|---|---|
| `MapPrepared` | preparation flow | 맵 준비 성공 | mapId, grid source, normalized bounds | Combat Map application layer |
| `PlayerSpawnResolved` | spawn policy | activation candidate 선택 | mapId, source, position | activation flow |
| `CombatMapActivated` | activation flow | map state와 active relation commit | mapId, adventureId, version | Adventure Runtime response |
| `VisibilityRefreshed` | CombatMap | spawn/move/door change | current/explored counts, ruleTurn | player projection |

## 2.4 Policies

| Policy | Trigger | Decision | Owner |
|---|---|---|---|
| `PrintedGridAcceptancePolicy` | printed-grid candidate 생성 | candidate를 실제 Printed Grid로 채택할지 결정 | Combat Map internal capability |
| `FallbackGridPolicy` | candidate 없음/거절 | 안정적인 정사각형 fallback grid 생성 | Combat Map internal capability |
| `SpawnResolutionPolicy` | map activation | explicit candidate → entry hint → safe fallback 순으로 valid cell 선택 | Combat Map |
| `VisibilityPolicy` | activation/movement/door change | finite vision envelope + blockers로 current/explored 계산 | CombatMap domain |
| `VisibilityFailClosedPolicy` | visibility 계산 예외 | PLAYER origin만 공개 | Combat Map projection |

## 2.5 Read Models

| Read Model | Consumer | Source | Fields | Owner |
|---|---|---|---|---|
| `PlayerCombatMapView` | web-ui / Solo Player | CombatMap + VisibilitySnapshot | grid, visible tokens, explored obstacles/doors, player-visible layers, current, explored, version | Combat Map |
| GM CombatMap view | AI/authorized internal caller | CombatMap | full token/layer/runtime state | Combat Map |

## 2.6 External Interactions

| External System | Trigger | Input | Output | Failure |
|---|---|---|---|---|
| Adventure Runtime | map activation | versioned map id + entry/spawn context | active map/version | version conflict, invalid spawn |
| AI Game Master | tactical materialization | normalized placements/hidden regions | validated tactical candidates | invalid/colliding candidate rejected |
| PostgreSQL | prepare/activate/runtime command | CombatMap state | versioned persisted state | transaction rollback |
| web-ui | player map read | player projection | map rendering | missing/invalid projection is fail-closed |

## 2.7 Hotspots

| Hotspot | Options | Decision |
|---|---|---|
| Grid detector false positive | always accept / user editor / confidence gate | confidence + regularity gate; 실패 시 fallback |
| Printed grid가 매우 큼 | 20x20 강제 축소 / 원본 유지 | 원본 grid를 유지. 성능 문제는 projection/UI 최적화로 다루고 의미를 변경하지 않음 |
| Crop 오탐 | aggressive crop / conservative crop | edge-connected low-information만 제거하고 confidence 낮으면 원본 bounds 유지 |
| Spawn 정보 부족 | `(0,0)` / activation fail / safe fallback | deterministic safe boundary fallback, source를 FALLBACK으로 기록 |
| blocker 없는 visibility | 전체 공개 / 제한된 탐색 범위 | finite `VisibilityProfile` 적용; 전체 공개 금지 |
| `INITIAL_FOG` | Fog의 정본 / 추가 authoring mask | 정본은 VisibilitySnapshot, `INITIAL_FOG`는 선택적 추가 mask |

---

# 3. DDD Architecture

## 3.1 Bounded Contexts

| Bounded Context | Responsibility | Ubiquitous Language | Owned Model | Owned Data |
|---|---|---|---|---|
| Combat Map | 지도 준비, 격자 좌표계, 토큰 위치, 이동, 문/장애물, visibility/exploration | CombatMap, Grid, Token, Spawn, Visibility | CombatMap aggregate | map state, tokens, layers, visibility, active map relation |
| Adventure Runtime | 현재 모험 진행과 map command 조정 | Adventure, Turn, Situation, Runtime Command | runtime aggregate들 | session/runtime state |
| AI Game Master | 저장 권한 없는 전술/시작 위치 후보 생성 | tactical candidate, start-position candidate | proposal DTO | 정본 map state 없음 |

### 3.1.1 Boundary Decisions

| Capability | Owner Context | Candidate Boundary | Chosen Boundary | Why Not Weaker? | Why Not Stronger? |
|---|---|---|---|---|---|
| Image crop/grid preparation | Combat Map | helper / internal capability / new BC | internal capability | 단일 helper보다 여러 정책과 테스트 seam이 필요 | 독립 state/lifecycle이 없어 BC/service 불필요 |
| Spawn resolution | Combat Map | domain policy / new BC | domain/application policy | grid validity·occupied cell을 함께 검증해야 함 | CombatMap 없이 독립적으로 의미 없음 |
| Fog/visibility | Combat Map | aggregate policy / new service | existing domain policy + projection | token/door/obstacle 상태와 일관성 필요 | 이미 Combat Map의 핵심 소유권 |

새 Bounded Context, 새 module, 새 deployment unit은 추가하지 않는다.

## 3.2 Context Map

`CONTEXT-MAP.md`의 경계는 변경하지 않는다. Adventure Runtime은 Combat Map 상태를 복제하지 않고 versioned command로 조정하며, AI Game Master의 시작 위치/전술 배치는 후보일 뿐 Combat Map이 grid validity와 충돌 여부를 최종 검증한다.

따라서 이번 티켓에서 `CONTEXT-MAP.md` 수정은 필요하지 않다.

## 3.3 Aggregates

| Aggregate | Root | Responsibility | Commands | Invariants |
|---|---|---|---|---|
| CombatMap | `CombatMap` | grid 안의 token/obstacle/door/runtime visibility 일관성 | activate-related mutation, move, door change, tactical effects | 모든 위치는 grid 내부, occupied collision 금지, active projection에는 PLAYER origin 존재 |

현재 생성 시 "최소 한 토큰"을 강제하기 위해 application layer가 PLAYER를 `(0,0)`에 보충하는 방식은 제거한다. **준비된 맵은 token이 0개일 수 있다.** 활성화 transaction의 precondition이 PLAYER Spawn을 보장한다. Aggregate의 위치·충돌 invariant는 유지한다.

## 3.4 Entities

| Entity | Aggregate | Identity | Responsibility | State |
|---|---|---|---|---|
| CombatToken | CombatMap | TokenId | 위치, controller, discovery | type, position, owner, discovery |
| Door | CombatMap | grid position의 도메인 identity | visibility blocker 상태 | position, open |

## 3.4.1 Class Diagram

변경 대상이 있으므로 class diagram 적용 대상이다.


원본: `docs/specs/278/diagrams/architecture/combat-map.class.puml` · [렌더링 SVG](diagrams/architecture/combat-map.class.svg)

## 3.5 Value Objects

| Value Object | Owner | Values | Validation | Behavior |
|---|---|---|---|---|
| `MapContentBounds` | preparation | x, y, width, height, confidence | image 내부, 양의 크기 | crop rectangle 표현 |
| `GridCalibration` | preparation | GridSpec, originX/Y, pixel bounds, GridSource, confidence | 양수 cell, image 내부 bounds | `GRID_BOUNDS`/metadata 생성 |
| `GridSource` | preparation | `PRINTED`, `FALLBACK` | enum | grid provenance 구분 |
| `MapActivationContext` | application | stagePosition, optional normalized/grid spawn candidate, optional entry side | stage > 0, normalized candidate는 0..1 | activation input |
| `SpawnResolution` | application/domain policy | position, source | valid grid cell | 선택 결과 추적 |
| `VisibilityProfile` | domain | maxRangeCells | 1 이상 | 현재 시야의 최대 범위 제한 |

`GridCalibration`은 gameplay aggregate의 장기 독립 entity가 아니다. 저장 시 기존 `GridSpec`, `MAP_IMAGE`, `GRID_BOUNDS`와 AI_ONLY `GRID_META` layer로 축약할 수 있으므로 별도 table을 만들지 않는다.

## 3.6 Domain Services

| Domain Service | Responsibility | Input | Output | Collaborators |
|---|---|---|---|---|
| `SpawnResolutionPolicy` | 유효한 PLAYER 시작 셀 선택 | grid, obstacles, doors, occupied, activation candidates | SpawnResolution | GridSpec/GridPosition |
| `VisibilityPolicy` | bounded line-of-sight/current/explored 계산 | grid, player origins, blockers, prior explored, profile | VisibilitySnapshot | Door, CombatToken |

`MapContentBoundsDetector`, `MapGridDetector`, `FallbackGridPolicy`는 이미지 처리/application preparation component이며 새로운 domain service/BC로 승격하지 않는다.

## 3.7 Business Rule Ownership

| Business Rule | Owner | Enforcement Point |
|---|---|---|
| BR-1/BR-2 | preparation pipeline | PrintedGridAcceptancePolicy → FallbackGridPolicy 분기 |
| BR-3 | MapContentBoundsDetector | low-confidence일 때 full-source bounds 유지 |
| BR-4 | CombatMap/GridSpec | token/obstacle/door position validation |
| BR-5 | web-ui | tactical grid CSS |
| BR-6/BR-7 | CombatMap + VisibilityPolicy | refreshVisibility |
| BR-8/BR-13 | Player projection | visible token/layer filtering + legend derivation |
| BR-9~BR-11 | SpawnResolutionPolicy + activation transaction | activate precondition |
| BR-12 | deterministic preparation policies | fixture/property tests |

## 3.8 Aggregate State Transitions

새 persisted lifecycle enum을 추가하지 않는다. 준비/활성 상태는 기존 active-map relation과 PLAYER/visibility 상태의 application contract로 관리한다.

| Current State | Command | Next State | Preconditions | Result |
|---|---|---|---|---|
| Prepared, inactive | ActivateCombatMap | Active | owner/version valid, spawn resolvable | PLAYER token + visibility + active relation |
| Active | MoveToken | Active | expectedVersion, movement valid | token moved + visibility refreshed |
| Active | ChangeDoor | Active | expectedVersion | door changed + visibility refreshed |
| Active | Activate another map | Inactive for current adventure | new map activation succeeds | active relation moves to new map |

## 3.8.1 State Diagram

설계 상태 다이어그램 적용 대상이다.

원본: `docs/specs/278/diagrams/architecture/combat-map-runtime.state.puml` · [렌더링 SVG](diagrams/architecture/combat-map-runtime.state.svg)

## 3.9 Repository Boundaries

| Repository | Aggregate | Operations | Consistency Boundary |
|---|---|---|---|
| `CombatMapViewStore` / Postgres implementation | CombatMap | insert, find, versioned update, activate | one map version |
| 확장 `activate(...)` operation | CombatMap + active-map pointer | map mutation + active relation update | **한 DB transaction** |

새 activation contract는 `update(map)` 후 `activate(pointer)`를 두 번 독립 호출하지 않는다. Player Spawn/visibility는 성공했지만 active pointer 갱신이 실패하는 partial state를 방지하기 위해 store에 원자적 activation operation을 추가한다.

---

# 4. Program Design

## 4.1 Program Structure

새 module 없이 `combat-map-service` 내부를 다음 책임으로 나눈다.

- API/configuration: HTTP 및 bean wiring
- application/view: player/GM projection과 existing view service
- application/preparation: raster normalization, grid candidate/fallback policy
- domain: CombatMap, Spawn/Visibility rules
- infrastructure/persistence: atomic versioned persistence/activation
- web-ui: player-only rendering

기존 preparation 클래스 이동이 과도한 churn을 만들면 이번 티켓에서는 package 이동 없이 동일 책임 분리를 클래스 수준으로 먼저 적용할 수 있다. 새 module 생성은 금지한다.

## 4.2 Major Components and Responsibilities

| Component | Responsibility | Input | Output | Must Not Do |
|---|---|---|---|---|
| `MapFilePreparationAdapter` | image/PDF decode와 pipeline 호출 | UploadedMapSource | PreparedMapData | spawn 결정, visibility 계산 |
| `MapContentBoundsDetector` | edge-connected 불필요 여백 candidate | BufferedImage | MapContentBounds | grid fallback 생성 |
| `MapGridDetector` | Printed Grid candidate 검출 | normalized BufferedImage | `Optional<DetectedMapGrid>` | no-grid를 20x20 detected grid로 위장 |
| `PrintedGridAcceptancePolicy` | candidate 신뢰성 평가 | lines/period/confidence | accept/reject | fallback 생성 |
| `FallbackGridPolicy` | no-grid map의 deterministic grid | normalized dimensions | GridCalibration | Printed Grid라고 표시 |
| `MapPreparationPipeline` | crop → detect → fallback → layers 조립 | decoded image | PreparedMapData + metadata | PLAYER spawn 결정 |
| `SpawnResolutionPolicy` | activation 시 valid spawn | map state + context | SpawnResolution | scenario prose 해석 |
| `CombatMapViewService` | prepare/activate/read orchestration | app commands | CombatMap/view | image CV 세부 알고리즘 보유 |
| `CombatMap` | runtime invariant/visibility state | domain commands | versioned state | source image parsing |
| `VisibilityPolicy` | finite LOS 계산 | origins, blockers, profile | VisibilitySnapshot | player UI 표현 |
| `PlayerSafeFogProjection` | AI_ONLY mask 포함 player-safe projection | snapshot/layers | filtered current/explored | 전체 map fail-open |
| `CombatMapView.tsx` | typed player projection 표시 | CombatMapView DTO | grid/fog/token UI | hidden state 추론 |

## 4.3 Application Flow

### Prepare Uploaded Map

1. `CombatMapViewService.prepareUploaded`가 `MapFilePreparationPort.prepare` 호출.
2. adapter가 image 또는 PDF 첫 페이지를 raster로 decode.
3. pixel-count/size guard 검증.
4. `MapContentBoundsDetector`가 candidate bounds를 계산.
5. confidence가 기준 미만이면 전체 source bounds 사용. 기준 이상이면 crop.
6. cropped image에 `MapGridDetector.detect` 수행.
7. candidate가 `PrintedGridAcceptancePolicy`를 통과하면 `GridSource.PRINTED`.
8. 없거나 거절되면 `FallbackGridPolicy`로 `GridSource.FALLBACK`.
9. normalized raster를 PNG로 encode하고 `MAP_IMAGE`, `GRID_BOUNDS`, AI_ONLY `GRID_META`를 만든다.
10. `PreparedMapData`로 CombatMap을 저장. PLAYER를 암묵적으로 추가하지 않는다.

### Activate Map

1. Adventure Runtime이 map id/version과 `MapActivationContext` 전달.
2. `CombatMapViewService`가 owned/versioned map 로드.
3. 기존 explicit tactical PLAYER placement가 있으면 candidate로 우선 사용.
4. 그 외 runtime entry candidate가 있으면 grid cell로 변환·검증.
5. 후보가 없거나 invalid이면 `SpawnResolutionPolicy`가 deterministic boundary fallback을 찾는다.
6. valid cell이 없으면 activation 실패.
7. PLAYER token을 생성/이동하고 `VisibilityPolicy`로 최초 snapshot 생성.
8. map state와 active-map relation을 한 transaction으로 commit.
9. player projection 반환.

## 4.4 Component Call Contracts

| Order | Caller | Callee | Operation | Input | Output | Failure |
|---:|---|---|---|---|---|---|
| 1 | CombatMapViewService | MapFilePreparationPort | prepare | UploadedMapSource | PreparedMapData | MAP_SOURCE_UNREADABLE |
| 2 | preparation adapter | MapContentBoundsDetector | detect | raster | bounds | confidence-low → full bounds |
| 3 | preparation pipeline | MapGridDetector | detect | normalized raster | Optional candidate | no candidate 정상 |
| 4 | preparation pipeline | PrintedGridAcceptancePolicy | accept | candidate evidence | boolean | none |
| 5 | preparation pipeline | FallbackGridPolicy | create | normalized size | calibration | invalid dimension |
| 6 | CombatMapViewService | SpawnResolutionPolicy | resolve | map/context | SpawnResolution | NO_VALID_PLAYER_SPAWN |
| 7 | CombatMapViewService | CombatMap | apply spawn + refresh | resolved position/profile | updated state | invariant failure |
| 8 | CombatMapViewService | CombatMapViewStore | activate atomically | map/version/stage | new version | VERSION_CONFLICT/DB failure |

## 4.5 Major Types

| Type | Kind | Responsibility |
|---|---|---|
| `MapContentBounds` | immutable application value | normalized crop rectangle |
| `DetectedMapGrid` | candidate value | Printed Grid evidence |
| `GridCalibration` | immutable application value | pixel/logical grid mapping |
| `GridSource` | enum | PRINTED/FALLBACK 구분 |
| `MapActivationContext` | command DTO/value | runtime entry context |
| `SpawnResolution` | value | chosen cell + source |
| `VisibilityProfile` | domain value | finite vision range |
| `VisibilitySnapshot` | domain value | current/explored/observed/lastSeen |

## 4.6 Type Design

### `GridCalibration`

| Field | Meaning | Constraint |
|---|---|---|
| `grid` | logical GridSpec | width/height/cell > 0 |
| `originX`, `originY` | normalized raster 안 첫 grid origin | >= 0 |
| `pixelWidth`, `pixelHeight` | grid가 차지하는 pixel bounds | > 0, image bounds 안 |
| `source` | PRINTED/FALLBACK | required |
| `confidence` | Printed candidate 품질; FALLBACK은 0 | 0..1 |

### `MapActivationContext`

| Field | Meaning | Constraint |
|---|---|---|
| `stagePosition` | 기존 stage-compatible active map index | > 0 |
| `spawnCandidate` | upstream이 제공한 명시적/normalized 위치 | optional, 제공 시 range valid |
| `entrySide` | map edge 힌트 | optional |

Combat Map은 `entrySide`나 좌표 candidate를 검증하지만 시나리오 자연어를 직접 파싱하지 않는다. 자연어에서 시작 위치 후보를 만드는 책임은 Scenario/AI 측에 남긴다.

### `VisibilityProfile`

이번 티켓의 기본 profile은 `maxRangeCells = 6`으로 두고 configuration으로 override 가능하게 한다. 이는 D&D 규칙의 영구 상수가 아니라 **blocker metadata가 부족한 맵에서도 전체 맵이 즉시 공개되는 것을 막는 presentation-safe fallback**이다. 향후 RuleSet/scene이 명시적 sight range를 제공하면 해당 값이 우선한다.

## 4.7 Interfaces and Function Signatures

```java
interface MapGridDetectionPort {
    Optional<DetectedMapGrid> detect(BufferedImage image);
}
```

- `detect`는 Printed Grid evidence만 책임진다.
- lines가 부족하거나 regularity가 낮으면 `Optional.empty()`를 반환한다.
- `dimension / 20` fallback은 이 인터페이스에서 제거한다.

```java
final class SpawnResolutionPolicy {
    SpawnResolution resolve(
        GridSpec grid,
        Set<GridPosition> obstacles,
        Collection<Door> doors,
        Collection<GridPosition> occupied,
        MapActivationContext context,
        Optional<GridPosition> tacticalPlayerPlacement);
}
```

우선순위:
1. explicit tactical PLAYER placement
2. activation spawn candidate
3. entry-side와 일치하는 valid boundary cell
4. deterministic valid boundary scan
5. 없으면 `NoValidPlayerSpawnException`

```java
interface CombatMapViewStore {
    long activate(
        MapOwnerId owner,
        CombatMap map,
        long expectedVersion,
        int stagePosition,
        UUID operationKey,
        String operationFingerprint);
}
```

해당 operation은 map update와 adventure active-map relation을 동일 transaction에서 commit한다.

## 4.8 Error Propagation

| Failure Point | Source Error | Converted Error | Handler | Result |
|---|---|---|---|---|
| image/PDF decode | unsupported/corrupt bytes | `MAP_SOURCE_UNREADABLE` | preparation boundary | prepare 실패 |
| content bounds detection | low confidence | error 아님 | pipeline | full image 사용 |
| Printed Grid detection | no/ambiguous evidence | error 아님 | pipeline | FALLBACK grid |
| PNG normalization | encode failure | `MAP_PREPARATION_FAILED` | app service | map 저장 안 함 |
| spawn resolution | no valid cell | `NO_VALID_PLAYER_SPAWN` | activation service | activation rollback |
| optimistic update | version mismatch | existing version conflict | caller retry/reload | stale command 미적용 |
| visibility calculation | unexpected calculation failure | `VISIBILITY_DEGRADED` internal signal | projection/domain fallback | PLAYER origin만 current/explored로 공개 |

## 4.9 State Transition Implementation

| State Transition | Domain Owner | Method/Flow | Persistence Point |
|---|---|---|---|
| source → prepared map | application + CombatMap | `prepareUploaded` | store.insert |
| prepared → active | Combat Map | `activateForAdventure` revised flow | atomic store.activate |
| active → moved | CombatMap | existing movement flow + refreshVisibility | versioned update |
| active → changed door | CombatMap | changeDoor + refreshVisibility | versioned update |

## 4.10 Dependency Rules

### Allowed Dependencies

| Source | Target | Contract |
|---|---|---|
| API/config | application | ports/services |
| application preparation | JDK image primitives / PDF adapter boundary | BufferedImage / decoded raster |
| application | domain | CombatMap, GridSpec, policies |
| infrastructure | application/domain | store interfaces/models |
| web-ui | player API | PlayerCombatMapResponse contract |

### Forbidden Dependencies

| Source | Forbidden Target |
|---|---|
| CombatMap domain | React/web-ui, PDFBox, HTTP DTO |
| MapGridDetector | Adventure Runtime/AI service |
| web-ui | GM/AI_ONLY layers or raw persistence |
| Adventure Runtime | replicated CombatMap state calculations |
| preparation capability | new independent deployment service |

---

# 5. Technical Architecture

## 5.1 Boundary Mapping

| Bounded Context | Internal Capability | Code Boundary | Deployment Unit | Rationale |
|---|---|---|---|---|
| Combat Map | map preparation | package/classes in `combat-map-service` | `combat-map-service` | state 독립성 없음 |
| Combat Map | spawn/visibility | domain/application policies | `combat-map-service` | map aggregate와 transaction 공유 |
| Combat Map | player presentation | API projection + web-ui consumer | existing services | hidden state filtering은 server owner가 수행 |

## 5.2 Boundary Promotion Decisions

| Candidate | Chosen Boundary | Why Not Weaker? | Why Not Stronger? | Cost |
|---|---|---|---|---|
| image preparation | internal package/capability | 단일 메서드로는 crop/grid/fallback 테스트 seam 부족 | 별도 module/service는 데이터·운영 lifecycle이 없음 | 몇 개 타입/정책 추가 |
| spawn resolution | policy class | aggregate 외 entry candidate 해석 필요 | 새 BC는 Combat Map invariant를 쪼갬 | 작은 application/domain seam |
| fog visibility | existing domain policy | UI-only 처리 시 hidden token 누출 가능 | 독립 service는 map state 동기화 필요 | 기존 policy signature 변경 |

## 5.3 System Interaction Flow

동기 흐름을 유지한다.

- Adventure Runtime → Combat Map: versioned prepare/activate/map commands.
- Combat Map → PostgreSQL: versioned aggregate persistence.
- web-ui → Adventure/Combat Map player endpoint: player-safe projection.
- AI Game Master → Adventure Runtime → Combat Map: 검증되지 않은 tactical/spawn 후보.

새 message broker/event bus는 추가하지 않는다.

## 5.4 Synchronous Communication

Activation contract는 기존 stage-position 호환성을 유지하면서 `MapActivationContext`를 전달하도록 확장한다. upstream이 spawn candidate를 제공하지 않아도 Combat Map이 safe fallback을 결정할 수 있으므로 모든 기존 호출자가 동시에 semantic spawn을 제공할 필요는 없다.

## 5.5 Data Ownership and Persistence

- `GridSpec`, tokens, obstacles, doors, VisibilitySnapshot의 정본은 Combat Map.
- normalized `MAP_IMAGE`와 pixel mapping `GRID_BOUNDS`는 기존 MapLayer 저장 경계를 유지한다.
- `GRID_META`를 AI_ONLY layer로 추가해 `source=PRINTED|FALLBACK`, confidence, crop metadata를 관찰/진단용으로 보존한다. Player projection에는 반환하지 않는다.
- 별도 table/column migration은 만들지 않는다.
- Active map relation은 기존 persistence를 사용하되 activation transaction에서 map version update와 함께 commit한다.

## 5.6 Image Normalization

- 입력 image와 PDF 첫 페이지를 `BufferedImage`로 decode한다.
- crop이 적용되든 아니든 최종 `MAP_IMAGE`는 normalized PNG로 encode한다.
- `MapContentBoundsDetector`는 이미지 edge와 연결된 저정보 strip만 제거 후보로 삼는다. 내부의 흰 방/복도는 crop 근거가 아니다.
- crop confidence가 설정 threshold보다 낮으면 원본 전체를 사용한다.
- crop 후 grid detection을 수행해 origin 좌표계가 항상 normalized raster 기준이 되게 한다.

외부 OpenCV/native dependency는 이번 티켓에 추가하지 않는다. 현재 JDK raster 처리로 fixture 요구를 충족하고, 향후 정확도 한계가 확인되면 별도 architecture decision으로 승격한다.

## 5.7 Printed Grid Detection and Fallback

`MapGridDetector`는 다음 evidence를 계산한다.

- 수평/수직 dark-line peak 후보.
- 각 축의 반복 gap median.
- gap regularity.
- X/Y period 유사성.
- usable line span.

초기 acceptance tuning 값:

- 각 축에서 반복 evidence가 최소 4개 line 이상.
- X/Y cell period 차이는 작은 허용 오차 안에 있어야 한다.
- aggregate confidence 기본 threshold `0.75`.

값은 configuration/constant로 격리하고 image fixture 테스트를 기준으로 조정 가능하게 한다. 중요한 contract는 **threshold 미달이 FALLBACK으로 가며 detected result로 위장되지 않는 것**이다.

기존 `width > 32 || height > 32 → 20x20` 의미 변경은 제거한다. 실제 Printed Grid가 크면 그대로 표현한다.

`FallbackGridPolicy`는 현재 코드가 암묵적으로 사용하던 약 20칸 밀도를 명시적 정책으로 옮긴다. 기본값은 짧은 축 기준 약 20칸이며 셀은 정사각형을 유지한다. `targetCells=20`은 configuration seam으로 둔다.

## 5.8 Player API and Web UI

현재 `PlayerCombatMapResponse`의 `grid`, `layers`, `current`, `explored`, player-safe `tokens` 구조를 유지해 API breaking change를 피한다.

- `GRID_BOUNDS`는 normalized image 기준으로 계속 전달한다.
- AI_ONLY `GRID_META`, `INITIAL_FOG`는 player response에 포함하지 않는다.
- `CombatMapView.tsx`는 active map에서 `current`가 없을 때 `true`로 fallback하지 않는다. `current ?? []`, `explored ?? []`로 fail-closed한다.
- PLAYER token 자체는 서버의 fail-closed projection에서도 보존한다.
- Token Legend는 고정 7종 목록이 아니라 player response에 실제 존재하는 token type 집합에서 생성한다. 숨겨진 token은 response에 없으므로 legend에도 나타나지 않는다.
- grid line은 cell 사이에 중복되지 않는 `1px solid #000` outline으로 렌더한다. 황색/highlight border는 사용하지 않는다.

## 5.9 Target File Impact

주요 변경 후보:

### Backend

- `src/combat-map-service/src/main/java/com/dndmaster/combatmap/application/view/MapGridDetector.java`
- `.../DetectedMapGrid.java`
- `.../MapGridDetectionPort.java`
- `.../CombatMapViewService.java`
- `.../PreparedMapData.java` — 필요 시 preparation metadata 전달만 확장
- `.../PlayerSafeFogProjection.java`
- `src/combat-map-service/src/main/java/com/dndmaster/combatmap/domain/CombatMap.java`
- `.../VisibilityPolicy.java`
- `.../CombatMapViewStore.java`
- `.../PostgresCombatMapViewStore.java`
- `.../CombatMapApiConfiguration.java`

신규 internal types/components 후보:

- `MapContentBounds.java`
- `MapContentBoundsDetector.java`
- `GridCalibration.java`
- `GridSource.java`
- `PrintedGridAcceptancePolicy.java`
- `FallbackGridPolicy.java`
- `MapActivationContext.java`
- `SpawnResolution.java`
- `SpawnResolutionPolicy.java`
- `VisibilityProfile.java`

### Frontend

- `src/web-ui/src/features/combat-map/CombatMapView.tsx`
- `src/web-ui/src/features/saved-adventures/AdventurePlayApi.ts` — activation/read contract가 변할 때만
- `src/web-ui/src/app.css`

---

# 6. Runtime Design

## 6.1 Runtime Ordering

Preparation 순서는 `decode → crop decision → grid detection → fallback decision → normalized encode → persist`로 고정한다. Grid detection을 crop 이전 좌표에서 수행한 뒤 UI에서 다시 offset 보정하는 이중 좌표계를 만들지 않는다.

Activation 순서는 `load/version check → spawn resolve → token state → visibility refresh → atomic persist+activate → player read`다.

Movement/door command는 기존 optimistic version ordering을 유지하고 state mutation 후 visibility를 동일 version에 포함한다.

## 6.2 Concurrent Access

- existing `expectedVersion` optimistic concurrency를 유지한다.
- activation도 expectedVersion을 받아 stale prepared map에 spawn을 덮어쓰지 않는다.
- 동시에 두 activation이 들어오면 하나만 version/active relation transaction에 성공한다.

## 6.3 Transaction Boundaries

- 이미지 분석은 DB transaction 밖에서 수행한다.
- 준비 결과 완성 후 `insert` 한 번으로 저장한다.
- activation의 PLAYER token, initial visibility, version update, active-map relation은 한 transaction이다.
- movement/door 변경은 기존 map version transaction 안에서 visibility snapshot까지 함께 저장한다.

## 6.4 Idempotency and Duplicate Handling

- 기존 command id / operation fingerprint 규칙을 activation에도 적용한다.
- 동일 command id + 동일 payload 재전송은 기존 성공 결과를 반환한다.
- 동일 command id를 다른 spawn/context payload에 재사용하면 conflict로 거절한다.
- Map preparation 자체는 새 map 생성 command이므로 호출자 수준의 기존 중복 제어 정책을 유지한다.

## 6.5 Partial Failure

- crop/grid 분석 실패 후 map row를 남기지 않는다.
- activation transaction 실패 시 PLAYER 위치와 active relation 둘 다 이전 상태로 rollback한다.
- player projection 실패는 aggregate를 되돌리지 않는다. fail-closed visibility를 반환하고 내부 오류를 기록한다.

---

# 7. Error Handling and Recovery

## 7.1 Failure Classification

| Category | Retryable | Recovery |
|---|---|---|
| corrupt/unsupported source | no | 사용자에게 source 준비 실패 |
| crop low confidence | n/a | full bounds 사용 |
| printed-grid ambiguous | n/a | fallback grid 사용 |
| PNG encode/storage failure | infrastructure-dependent | 준비 전체 재시도 가능 |
| invalid spawn candidate | n/a | 다음 candidate/fallback 탐색 |
| no valid spawn cell | no until state/input changes | activation 거절 |
| version conflict | yes after reload | 최신 map version으로 재시도 |
| DB activation failure | yes | transaction rollback 후 재시도 |
| visibility computation defect | internal | origin-only fail-closed + alert/log |

## 7.2 Retry Policy

Computer-vision 판단 결과 자체를 무작정 재시도하지 않는다. 동일 입력은 deterministic해야 한다. Infrastructure failure만 기존 retry boundary에서 재시도한다.

## 7.3 Compensation and Rollback

별도 compensation event는 필요하지 않다. activation은 단일 DB transaction으로 원자화해 보상보다 rollback을 선택한다.

## 7.4 Fail-closed Visibility

`VisibilityPolicy`가 예상치 못한 이유로 snapshot을 만들지 못하면 player read에서 전체 grid를 current로 생성하지 않는다.

Fallback snapshot:

- `current = PLAYER token positions`
- `explored = prior explored ∪ PLAYER positions` 또는 prior snapshot이 없으면 PLAYER positions
- `observedTokens = PLAYER tokens only`
- AI/HIDDEN token은 반환하지 않음

이 fallback은 오류를 숨기는 정상 규칙이 아니라 정보 누출을 막는 recovery path다.

---

# 8. Security

- 기존 owner authorization과 internal token/authentication 경계를 유지한다.
- AI_ONLY layer는 player projection에서 계속 제거한다. 새 `GRID_META`도 AI_ONLY다.
- hidden token filtering은 서버에서 완료하고 web-ui에 hidden token 목록을 보내지 않는다.
- Fog UI가 있어도 브라우저에 전달된 `MAP_IMAGE` 원본 바이트 자체는 사용자가 개발자 도구로 추출할 수 있다. Product Spec에서 정한 대로 이번 티켓은 정상 player UI의 정보 공개 제어이며 DRM/asset-level secrecy는 범위 밖이다.
- 업로드 raster는 decode 후 configurable max pixel count/dimension을 검증해 압축폭탄/과대 이미지가 heap을 소진하지 않게 한다.
- log에는 전체 base64 map data URL이나 원본 file bytes를 기록하지 않는다.

---

# 9. Observability

## 9.1 Structured Logs

Map preparation 성공 시 다음 값을 INFO/DEBUG 구조화 필드로 남긴다.

- map/adventure id
- source pixel size, normalized pixel size
- crop applied 여부와 crop ratio
- grid source `PRINTED|FALLBACK`
- grid dimensions/cell size
- printed-grid confidence

Activation 시:

- map/adventure id
- spawn source `TACTICAL|RUNTIME_HINT|ENTRY_SIDE|FALLBACK`
- resolved cell
- current/explored cell count

원본 이미지 byte/data URL, hidden token 상세는 로그에 남기지 않는다.

## 9.2 Metrics

권장 metric:

- `combat_map_prepare_total{grid_source}`
- `combat_map_grid_detection_confidence`
- `combat_map_crop_ratio`
- `combat_map_spawn_resolution_total{source}`
- `combat_map_spawn_failure_total`
- `combat_map_visibility_degraded_total`

## 9.3 Alerts

`visibility_degraded`가 연속 발생하거나 map activation failure 비율이 급증하면 오류로 취급한다. Printed Grid fallback 비율은 데이터 특성에 따라 높을 수 있으므로 단독 alert 조건으로 사용하지 않는다.

---

# 10. Change Boundaries

## 10.1 Allowed Changes

- `combat-map-service` 내부 preparation/policy/aggregate/projection 수정.
- existing store에 atomic activation operation 추가.
- web-ui의 grid/fog/legend 렌더링 수정.
- map-specific image fixtures 및 tests 추가.
- 필요하면 Adventure Runtime → Combat Map activation DTO에 optional spawn/entry hint 추가.

## 10.2 Forbidden Changes

- 새 Map Preparation Bounded Context 생성.
- 새 Gradle module 또는 독립 배포 service 생성.
- Adventure Runtime이 CombatMap token/fog state를 복제해 정본으로 보유.
- web-ui가 hidden token/layer를 받아 클라이언트에서만 숨기는 구조.
- Printed Grid를 20x20으로 강제로 재해석하는 fallback.
- Fog 문제를 단순 검정 overlay 이미지 하나로 대체해 runtime explored/current 상태를 제거.
- 다이어그램 파일 생성. 이번 작업에서는 별도 스킬 호출로 남긴다.

## 10.3 Conditional Changes

- OpenCV/native CV dependency는 JDK 기반 detector가 acceptance fixture를 충족하지 못한다는 측정 근거가 있을 때 별도 설계 결정으로 검토한다.
- manual crop/grid calibration UI는 자동 정책의 실사용 실패율이 충분히 확인된 후 별도 Product Spec으로 다룬다.
- DB schema migration은 existing MapLayer/active relation으로 요구사항을 표현할 수 없는 추가 영속 요구가 생길 때만 허용한다.

---

# 11. Verification Requirements

## 11.1 Domain / Unit Verification

`MapGridDetectorTest`를 확장해 다음 fixture를 고정한다.

- 명확한 Printed Grid → candidate present, expected origin/period 근접.
- no-grid image → `Optional.empty()`.
- 벽/텍스처 직선만 있는 image → false Printed Grid reject.
- 32칸을 넘는 실제 Printed Grid → 20x20으로 축소하지 않음.

새 `MapContentBoundsDetectorTest`:

- 큰 단색 외곽 여백 제거.
- 내부 흰 방/복도 보존.
- edge에 실제 콘텐츠가 닿으면 해당 방향 crop 금지.
- confidence 낮은 map은 full bounds.

새 `FallbackGridPolicyTest`:

- same dimensions → deterministic grid.
- no-grid landscape/portrait 모두 square cell policy 유지.

`SpawnResolutionPolicyTest`:

- tactical explicit candidate 우선.
- runtime candidate 검증.
- blocked/out-of-grid candidate reject 후 fallback.
- no valid cell → explicit failure.
- `(0,0)`은 fallback scan 결과 실제 선택된 경우에만 가능.

`VisibilityPolicy`/`CombatMapVisibilityIntegrationTest`:

- blocker 없는 맵도 기본 finite profile 때문에 전체 grid가 current가 되지 않음.
- 이동 후 current 변경, explored 누적.
- closed door/obstacle가 LOS 제한.
- hidden token은 current/exposure 조건 전까지 player view에 없음.
- degraded fallback은 player-origin만 공개.

## 11.2 Persistence / Integration Verification

- prepared token-empty map 저장 가능.
- activation 성공 시 PLAYER token, visibility, active relation이 동일 transaction으로 반영.
- version conflict 시 어느 것도 반영되지 않음.
- DB failure injection 시 partial activation 없음.
- `GRID_META` AI_ONLY가 player response에서 제외됨.

## 11.3 Frontend Verification

Combat Map component tests에서:

- missing `current`가 reveal-all로 해석되지 않음.
- hidden cell은 불투명 fog, explored/current 상태가 구분됨.
- black thin grid line style contract.
- map image bounds가 normalized crop 기준으로 표시됨.
- legend는 response에 존재하는 player-visible token types만 렌더.
- hidden token type은 legend/target button에 나타나지 않음.

## 11.4 Acceptance Trace

| Acceptance | Verification |
|---|---|
| AC-1~AC-3 | grid detector fixtures + integration render bounds |
| AC-4 | crop fixtures + normalized MAP_IMAGE dimensions |
| AC-5 | frontend style/component assertion |
| AC-6~AC-7 | activation/spawn integration tests |
| AC-8~AC-10 | visibility integration + player projection tests |
| AC-11 | legend component test |
| AC-12 | unreadable-source API/service test |
| AC-13 | repeated preparation deterministic test |

---

# 12. Alternatives, Trade-offs, Risks, and Open Questions

## 12.1 Alternatives Rejected

### 새 Map Preparation service

거절. Crop/grid detection은 독립 business lifecycle이나 consistency boundary가 없고 Combat Map을 준비하기 위한 내부 capability다. 별도 service는 API, deployment, failure mode만 늘린다.

### Frontend-only 수정

거절. CSS만 검정색으로 바꾸거나 overlay를 추가해도 잘못된 logical grid, `(0,0)` spawn, hidden-token projection 문제를 해결하지 못한다.

### 모든 맵을 고정 20x20으로 사용

거절. Printed Grid가 있는 경우 원본 좌표계를 따라야 한다는 Product Rule과 충돌한다. `20`은 no-grid fallback density에서만 사용한다.

### AI가 crop/grid/spawn을 전부 결정

거절. 동일 입력에 deterministic result가 필요하고, AI 후보는 저장 권한 없는 제안이라는 프로젝트 경계와 충돌한다. AI는 spawn/tactical candidate를 제공할 수 있으나 Combat Map이 검증한다.

### `INITIAL_FOG` 레이어를 generic Fog의 정본으로 사용

거절. 정적 cell mask는 플레이어 이동에 따른 explored/current lifecycle을 자연스럽게 소유하지 못한다. `VisibilitySnapshot`이 runtime 정본이고 `INITIAL_FOG`는 선택적 추가 authoring mask로 남는다.

### Manual grid/crop editor를 이번 티켓에 추가

보류. 현재 요구는 자동 준비 버그 수정이며 별도 사용자 편집 workflow는 범위를 크게 늘린다. 자동화 failure data가 쌓인 후 별도 제품 결정으로 다룬다.

## 12.2 Risks and Mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| 복잡한 아트에서 grid false positive | 토큰/거리 어긋남 | strict acceptance + fallback + fixture corpus |
| crop이 실제 map edge 제거 | 정보 손실 | conservative edge-connected crop + low-confidence full bounds |
| large printed grid DOM 비용 | frontend 성능 | grid 의미 유지, 필요 시 별도 virtualization 최적화 |
| upstream에 semantic spawn 정보 없음 | 잘못된 시작 방향 | source-tagged deterministic boundary fallback + observability |
| default finite vision range가 특정 시스템과 다름 | 시야가 보수적/넓을 수 있음 | configuration seam; future RuleSet-provided profile 우선 |
| 기존 `GRID_BOUNDS` string contract | parsing 취약 | 이번 티켓 호환 유지, malformed bounds fail-safe; typed API migration은 별도 가능 |

## 12.3 Open Questions

차단되는 Architecture open question은 없다.

다음 값은 contract가 아니라 fixture 기반 tuning parameter이며 구현 중 테스트 corpus에 맞춰 조정할 수 있다.

- Printed Grid confidence threshold 초기값 `0.75`.
- fallback target density 초기값 `20` cells on shorter axis.
- default visibility range 초기값 `6` cells.
- crop low-information/confidence threshold.

이 값의 조정은 `PRINTED 우선`, `ambiguous → FALLBACK`, `crop은 보수적`, `visibility는 fail-closed`라는 상위 계약을 변경해서는 안 된다.

---

# 13. Architecture 다이어그램 계약

이번 설계는 program flow, class responsibilities, activation state, runtime visibility flow가 변경되므로 Architecture diagram 적용 대상이다.

별도 호출의 다이어그램 계약:

| Diagram | 원본 경로 | 렌더링 SVG | 포함해야 할 계약 |
|---|---|---|---|
| Class | `docs/specs/278/diagrams/architecture/combat-map.class.puml` | [SVG](diagrams/architecture/combat-map.class.svg) | CombatMap, preparation policies, SpawnResolutionPolicy, VisibilityPolicy, store dependency |
| Design State | `docs/specs/278/diagrams/architecture/combat-map-runtime.state.puml` | [SVG](diagrams/architecture/combat-map-runtime.state.svg) | prepared/inactive → active, move/door visibility refresh, activation failure guards |
| Program Structure | `docs/specs/278/diagrams/architecture/program-structure.puml` | [SVG](diagrams/architecture/program-structure.svg) | API → application → domain → store, web player projection |
| Preparation Sequence | `docs/specs/278/diagrams/architecture/map-preparation.sequence.puml` | [SVG](diagrams/architecture/map-preparation.sequence.svg) | decode → crop → printed detect/accept → fallback → persist |
| Activation/Visibility Sequence | `docs/specs/278/diagrams/architecture/map-activation-visibility.sequence.puml` | [SVG](diagrams/architecture/map-activation-visibility.sequence.svg) | entry context → spawn resolution → visibility → atomic activation → player projection |

Context Map의 Bounded Context 관계는 변경하지 않으므로 새로운 Context Map 다이어그램은 **해당 없음 — 기존 Combat Map 경계를 그대로 사용**한다.

**상태:** READY — `.puml` 원본을 저장소의 고정 PlantUML renderer로 SVG에 렌더하고, 5개 SVG의 비어 있지 않음과 링크를 검증했다.
