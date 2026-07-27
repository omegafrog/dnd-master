param(
    [switch]$ValidateOnly
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$workflowPath = Join-Path $repoRoot '.github\workflows\dnd-master-ci.yml'
$workflow = Get-Content -Raw -LiteralPath $workflowPath
$javaCommand = if ($env:JAVA_HOME) {
    Join-Path $env:JAVA_HOME 'bin\java.exe'
} else {
    'java'
}
if ($env:JAVA_HOME -and -not (Test-Path -LiteralPath $javaCommand)) {
    throw 'DND Master CI requires JAVA_HOME to reference a Java 21 JDK.'
}
$previousErrorActionPreference = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
try {
    $javaVersionOutput = & $javaCommand -version 2>&1
    $javaVersionExitCode = $LASTEXITCODE
} finally {
    $ErrorActionPreference = $previousErrorActionPreference
}
if ($javaVersionExitCode -ne 0 -or -not (($javaVersionOutput -join "`n") -match 'version "21\.')) {
    throw 'DND Master CI requires Java 21 LTS. Configure JAVA_HOME and PATH with a Java 21 JDK.'
}

$requiredTokens = @(
    'actions/setup-java@v4',
    'java-version: "21"',
    'actions/setup-node@v4',
    'node-version: "22"',
    'docker version',
    'docker compose -f dnd-master/infra/compose.yaml config',
    'identity-access-service',
    'adventure-service',
    'rule-knowledge-service',
    'character-management-service',
    'dice-roll-service',
    'combat-map-service',
    'ai-game-master-service',
    '-pl architecture-tests -am test',
    '-pl contract-tests -am test',
    '-pl system-tests -am verify',
    'npm --prefix dnd-master/web-ui run lint',
    'npm --prefix dnd-master/web-ui test -- --run',
    'npm --prefix dnd-master/web-ui run build',
    'npm --prefix dnd-master/web-ui run test:e2e'
)

$missing = @($requiredTokens | Where-Object { -not $workflow.Contains($_) })
if ($missing.Count -gt 0) {
    throw "CI workflow is missing required entries: $($missing -join ', ')"
}
if ($workflow.Contains('continue-on-error: true')) {
    throw 'CI workflow must not hide failures with continue-on-error.'
}

Write-Host 'DND Master CI manifest validation passed.'
if ($ValidateOnly) {
    exit 0
}

$maven = if ($IsWindows -or $env:OS -eq 'Windows_NT') {
    Join-Path $repoRoot 'dnd-master\mvnw.cmd'
} else {
    Join-Path $repoRoot 'dnd-master/mvnw'
}

& $maven -f (Join-Path $repoRoot 'dnd-master\pom.xml') test
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

& npm --prefix (Join-Path $repoRoot 'dnd-master\web-ui') ci
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& npm --prefix (Join-Path $repoRoot 'dnd-master\web-ui') run lint
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& npm --prefix (Join-Path $repoRoot 'dnd-master\web-ui') test -- --run
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& npm --prefix (Join-Path $repoRoot 'dnd-master\web-ui') run build
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& npm --prefix (Join-Path $repoRoot 'dnd-master\web-ui') run test:e2e
exit $LASTEXITCODE
