param(
    [switch]$ValidateOnly
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$workflowPath = Join-Path $repoRoot '.github\workflows\dnd-master-ci.yml'
$workflow = Get-Content -Raw -LiteralPath $workflowPath

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
