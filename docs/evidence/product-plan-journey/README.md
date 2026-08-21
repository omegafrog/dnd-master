# 모험 시작 제품 계획 검증 증거

검증일: 2026-08-20

Playwright UI fixture 여정은 로그인부터 파티 조립, 모험 시작 상태, GM 턴, 주사위 결과, 전투 맵 행동, 재접속, 세션 종료까지 통과했다. 스크린샷은 이 디렉터리에 저장했다.

초기 실제 `app-all` 서비스 여정은 `combatmap.api.ApiContractExceptionHandler`와 `character.api.ApiContractExceptionHandler`가 동일한 `apiContractExceptionHandler` 이름을 사용해 실패했다. 두 advice에 명시적 Bean 이름을 부여한 뒤 app-all이 기동됐고 health check가 `UP`을 반환했다.

따라서 이 자료는 실제 Potent Brew 자료 생성·전술 장면 활성화의 증거가 아니라, 제품 UI 여정과 모험 시작 후 화면 계약의 fixture 증거다.

| 단계 | 스크린샷 |
| --- | --- |
| 로그인 | [01-login.png](./01-login.png) |
| 파티 조립 | [02-party-assembly.png](./02-party-assembly.png) |
| 모험 시작 상태 | [03-adventure-started.png](./03-adventure-started.png) |
| GM 턴 | [04-gm-turn.png](./04-gm-turn.png) |
| 판정 결과 | [05-dice-result.png](./05-dice-result.png) |
| 맵 행동 | [06-map-action.png](./06-map-action.png) |
| 재접속 | [07-reconnected.png](./07-reconnected.png) |
| 종료 | [08-adventure-completed.png](./08-adventure-completed.png) |

실제 Potent Brew fresh Playwright는 필수 storybook JSON 전달 문제로 skip되어 전술 장면 생성·재시도·활성화·플레이어 projection 비공개는 아직 미검증이다. 기준 문서는 `docs/specs/product-spec.md`와 `docs/plans/038-5-potent-brew-e2e.md`다.
