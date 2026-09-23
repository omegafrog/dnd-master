export function UserPcAgentSetup() {
  const websocketUrl = 'ws://127.0.0.1:8081/ws/agent'
  const command = `curl -fsSL ${window.location.origin}/downloads/install-user-pc-agent.sh -o install-user-pc-agent.sh\nchmod +x install-user-pc-agent.sh\n./install-user-pc-agent.sh ${window.location.origin} '발급받은 연결 토큰'`

  return <section className="setup-panel" aria-labelledby="user-pc-agent-title">
    <h2 id="user-pc-agent-title">사용자 PC 에이전트 연결</h2>
    <p>사용자 PC에 실행 프로그램을 설치하면 게임 서버가 웹소켓으로 그 PC의 Codex 실행 환경에 요청을 전달합니다.</p>
    <p role="status">현재 연결 주소: <code>{websocketUrl}</code></p>
    <p><a href="/downloads/user-pc-agent-linux.tar.gz" download>Linux 설치 파일 다운로드</a> · <a href="/downloads/user-pc-agent-windows.zip" download>Windows 설치 파일 다운로드</a></p>
    <ol>
      <li>설치 파일을 다운로드하거나 설치 명령을 복사합니다.</li>
      <li>Codex 로그인이 완료된 PC에서 설치 프로그램을 실행합니다.</li>
      <li>서버에서 발급한 연결 토큰을 입력합니다.</li>
    </ol>
    <details>
      <summary>직접 설치하고 실행하기</summary>
      <pre><code>{command}</code></pre>
    </details>
    <p className="form-hint">연결 토큰은 짧은 만료 시간을 가진 값만 사용하세요. 설치 파일에는 Java 실행 환경이 포함되지 않으므로 사용자 PC에 Java 21 이상과 Codex가 필요합니다.</p>
  </section>
}
