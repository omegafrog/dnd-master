param(
  [Parameter(Mandatory = $true)][string]$ServerUrl,
  [Parameter(Mandatory = $true)][string]$AccessToken
)
$ErrorActionPreference = 'Stop'
$java = Get-Command java -ErrorAction SilentlyContinue
if (-not $java) { throw 'Java 21 이상이 필요합니다.' }
if (-not (Get-Command codex -ErrorAction SilentlyContinue)) { irm https://chatgpt.com/codex/install.ps1 | iex }
$target = if ($env:USER_PC_AGENT_DIR) { $env:USER_PC_AGENT_DIR } else { Join-Path $env:LOCALAPPDATA 'dnd-master\user-pc-agent' }
New-Item -ItemType Directory -Force -Path $target | Out-Null
$archive = Join-Path $target 'user-pc-agent.zip'
Invoke-WebRequest -Uri "$ServerUrl/downloads/user-pc-agent-windows.zip" -OutFile $archive
Expand-Archive -Force -Path $archive -DestinationPath $target
$env:RELAY_WEBSOCKET_URL = if ($env:RELAY_WEBSOCKET_URL) { $env:RELAY_WEBSOCKET_URL } else { "$($ServerUrl.Replace('https://','wss://').Replace('http://','ws://'))/ws/agent" }
$env:AGENT_ACCESS_TOKEN = $AccessToken
$env:CODEX_EXECUTABLE = if ($env:CODEX_EXECUTABLE) { $env:CODEX_EXECUTABLE } else { 'codex' }
& "$target\user-pc-agent\bin\user-pc-agent.bat"
