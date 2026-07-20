# API

이 프로젝트는 7개의 OpenAPI 3.1 정적 계약을 가진다. 계약 파일은 런타임 구현의 현재 경로와 완전히 동일하다고 가정하지 않으며, 명시된 계약 경로와 실제 노출 경로는 분리해서 읽어야 한다.

## Swagger 진입점

- `/swagger-ui/index.html`
- `/v3/api-docs`

## 정적 계약

| BC | 계약 파일 |
| --- | --- |
| Identity & Access | `contracts/identity-access/openapi.yaml` |
| Adventure | `contracts/adventure/openapi.yaml` |
| Rule Knowledge | `contracts/rule-knowledge/openapi.yaml` |
| Character Management | `contracts/character-management/openapi.yaml` |
| Dice Roll | `contracts/dice-roll/openapi.yaml` |
| Combat Map | `contracts/combat-map/openapi.yaml` |
| AI Game Master | `contracts/ai-game-master/openapi.yaml` |

## 지원 스키마

- Adventure: `schemas/candidate-rule.json`, `schemas/stream-event.json`
- Combat Map: `schemas/player-map-view.json`
- Rule Knowledge: `schemas/async-status.json`, `schemas/multipart-upload.json`, `schemas/source-location.json`

## 주의

- 정적 계약의 기준 경로는 `/api/v1/**`이다.
- 인증 컨트롤러는 명시적으로 `/api/v1/auth/**`를 사용한다.
- UI 클라이언트는 `/api/public/**`를 사용한다.
- 위 세 경로는 서로 다르므로, 계약과 런타임 UI의 경로가 완전히 같다고 적지 않는다.
- Playwright는 fixture 기반 UI 검증이고, 백엔드 E2E의 기준은 Java system-tests이다.
