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
