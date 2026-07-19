$ErrorActionPreference = 'Stop'
$root = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
. (Join-Path $root 'scripts\local-ai-common.ps1')

function Assert-Throws([scriptblock]$Action, [string]$Name) {
    try { & $Action; throw "$Name did not throw." } catch { if ($_.Exception.Message -eq "$Name did not throw.") { throw } }
}

$compose = Get-Content -Raw (Join-Path $root 'compose.local-ai.yml')
foreach ($text in @('network_mode: service:ollama', '127.0.0.1:11434:11434', 'SPRING_AI_OLLAMA_BASE_URL: http://localhost:11434', ':/root/.ollama')) {
    if ($compose -notmatch [regex]::Escape($text)) { throw "Missing topology contract: $text" }
}
if ($compose -match 'ollama pull') { throw 'Compose must not pull models automatically.' }
foreach ($script in @('verify-local-ai.ps1', 'benchmark-local-ai.ps1')) {
    if ((Get-Content -Raw (Join-Path $root "scripts\\$script")) -match 'ollama pull') { throw "$script must not pull models." }
}
if ((Get-Content -Raw (Join-Path $root 'scripts\prepare-local-ai.ps1')) -notmatch 'docker compose @compose exec ollama ollama pull') { throw 'Prepare must use explicit container-internal pull.' }
$benchmark = Get-Content -Raw (Join-Path $root 'scripts\benchmark-local-ai.ps1')
foreach ($text in @("'-Dtest=LocalAiBenchmarkRouteTest'", 'http://localhost:11434', 'for ($iteration = 1; $iteration -le 7;', 'if ($iteration -gt 2)', 'Ceiling($ordered.Count * 0.95)', 'docker compose @compose run --rm runner')) {
    if ($benchmark -notmatch [regex]::Escape($text)) { throw "Missing benchmark contract: $text" }
}
if ($benchmark -match 'Write-Host.*(prompt|response|secret)') { throw 'Benchmark must not log prompts, raw responses, or secrets.' }
foreach ($bad in @('', 'models', 'C:\\models', 'D:\\models')) { Assert-Throws { Get-LocalAiModelRoot -ModelRoot $bad } "Rejected root [$bad]" }
Write-Host 'PASS local AI orchestration static contracts and rejected storage fixtures.'
