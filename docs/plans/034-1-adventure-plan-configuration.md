# 034-1 모험 길이·결말 설정과 생성 계약

Status: `completed`

Dependencies: 없음

## 구현 목적

Solo Player가 모험 계획을 만들기 전에 모험 길이와 결말 수를 선택한다. 선택값은 1~4개의 결말과 길이에 맞는 단계 수를 생성하는 AI 계약까지 보존하며, 자동 생성으로 사용자의 의도를 덮어쓰지 않는다.

## 구현 범위

- `endingCount`(1~4), `adventureLength`(SHORT, STANDARD, LONG)를 검증하는 설정 값 객체와 생성 요청을 추가한다.
- POST story-plan 및 retry 요청이 설정을 받고, 설정을 AI GM 요청에 전달한다.
- AI GM은 결말 수와 길이에 맞는 outline을 반환·검증한다.
- story-plan 화면에서 슬라이더/선택으로 설정하고 명시적으로 생성한다.
- 기존 저장된 플랜은 읽을 수 있고, 기존 생성 요청은 STANDARD/2로 호환한다.

## 수용 기준

- 결말 수 0 또는 5는 API와 도메인에서 거부한다.
- 1개 결말 요청은 단일 ending ID를 가진 유효한 플랜을 만든다.
- SHORT/STANDARD/LONG 요청은 각각 정해진 단계 예산을 AI에 전달한다.
- 최초 진입은 설정 화면을 보여 주며 자동 생성하지 않는다.
- Java 단위 테스트와 story-plan UI 테스트가 설정 전달을 검증한다.

## 검증

- `:adventure-service:test`
- `:ai-game-master-service:test`
- `npm test -- --run src/features/adventure-session/AdventureStoryPlanPage.test.tsx`
