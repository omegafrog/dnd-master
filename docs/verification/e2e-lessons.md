# E2E 실패 재발 방지

## E2E-CONTAINER-STARTUP-READY-01

- 수정일: 2026-10-22
- 적용 범위: `adventure-runtime-container`를 준비 단계로 사용하는 컨테이너 E2E
- 원인: 컨테이너 서비스는 분리 실행되지만 프로필은 `docker compose logs --follow`에 계속 붙어 있어 성공 영수증을 반환하지 않았다. 별도 시도는 Ollama 이미지 pull이 준비 단계 200초 제한에 걸려 중단됐다.
- 다음 실행 전 검사: 프로필이 health와 VictoriaMetrics `up=1` 확인 뒤 성공 종료하는지 확인하고, 앱 준비와 브라우저 검증을 별도 명령으로 진행한다. 준비 단계는 이미지 다운로드가 끝날 때까지 충분한 시간 제한을 사용한다.
- 예방 설정: [컨테이너 실행 프로필](../agents/execution.json), [실행 스크립트](../../scripts/container-runtime.sh), [실행 안내](../agents/EXEC.md), [E2E 실행 지침](../../.agents/skills/e2e-test/SKILL.md).
- 마지막 검증 결과: 셸·JSON·Compose 설정 검사는 통과. headed Chromium은 Playwright 데몬 소켓 경로가 205바이트로 길어 시작하지 못해 브라우저 검증은 미완료.
- source revision: `8dcf12443bd571e8f4ab61b9a4509618b7d259b5` (수정 전 기준)
