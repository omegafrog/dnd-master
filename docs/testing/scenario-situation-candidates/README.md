# 모험 상황 생성 수동 E2E

모험 시작 뒤에도 headed Chromium을 사용한다. CUA Driver는 쓰지 않는다. Playwright CLI는 화면 준비와 한 단계씩의 UI 조작에 사용하고, 실제 게임 마스터 응답 전후에 스크린샷을 확인해 응답 내용에 따라 다음 입력을 정한다. 고정 응답이나 미리 정한 입력 순서는 쓰지 않는다. 실행·연결·증거 저장은 [저장소 E2E 실행 절차](../../agents/EXEC.md), [브라우저 절차](../../agents/BROWSER.md), [ADR-024](../../adr/ADR-024-agent-driven-runtime-e2e.md)을 따른다.

- [이야기 길이와 필수 상황 그래프 진행 실제 화면 확인](./runtime-situation-transition.md)
