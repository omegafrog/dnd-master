# 사용자 PC 에이전트

사용자 PC 에이전트는 relay에 WebSocket으로 연결한 뒤, 서버가 보낸 요청을 PC의 Codex 로그인 정보로 실행하고 결과를 relay에 돌려준다.

## 실행

```bash
export RELAY_WEBSOCKET_URL=ws://127.0.0.1:8080/ws/agent
export AGENT_ACCESS_TOKEN='<identity-access 로그인 토큰>'
export CODEX_EXECUTABLE=/path/to/codex
export CODEX_WORK_DIRECTORY=/path/to/workspace
export CODEX_TIMEOUT=PT5M

./gradlew :user-pc-agent:installDist
./user-pc-agent/build/install/user-pc-agent/bin/user-pc-agent
```

`RELAY_WEBSOCKET_URL`과 `AGENT_ACCESS_TOKEN`은 필수다. 나머지는 기본값을 사용한다.

relay와의 WebSocket 연결이 끊기거나 처음 연결에 실패하면 에이전트는 프로세스를 종료하지 않는다. 1초부터 시작해 최대 30초까지 늘어나는 대기 시간과 작은 무작위 지연을 두고 다시 연결한다. 첫 연결이 성공한 뒤 재연결할 때는 새 연결 식별자를 발급해, 늦게 도착한 이전 연결의 종료 처리가 새 연결까지 끊지 않게 한다. 연결 상태와 재시도 횟수는 로그에 남는다. 프로세스 종료 신호를 받으면 재시도를 멈춘다.

진행 중이던 요청이 연결 단절로 실패하면 자동으로 같은 요청을 다시 보내지 않는다. 서버가 요청을 이미 처리했는지 확인할 수 없어 중복 작업 위험이 있기 때문이다. 연결이 복구된 뒤 해당 모험 화면에서 실패한 작업을 확인하고 다시 제출한다.
