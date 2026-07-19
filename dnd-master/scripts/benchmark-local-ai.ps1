param([int]$MinimumFreeGb = 20)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
. (Join-Path $PSScriptRoot 'local-ai-common.ps1')
& (Join-Path $PSScriptRoot 'verify-local-ai.ps1') -MinimumFreeGb $MinimumFreeGb
$compose = Get-LocalAiComposeArguments -ProjectRoot $root
$command = @('-pl', 'ai-game-master-service,rule-knowledge-service', '-am', '-Dtest=LocalAiBenchmarkRouteTest', '-Dspring.ai.ollama.base-url=http://localhost:11434', '-Dlocal-ai.ollama.base-url=http://localhost:11434', 'test')
$samples = [System.Collections.Generic.List[double]]::new()
for ($iteration = 1; $iteration -le 7; $iteration++) {
    $watch = [Diagnostics.Stopwatch]::StartNew()
    & docker compose @compose run --rm runner @command
    $watch.Stop()
    if ($LASTEXITCODE -ne 0) { throw "Namespace-sharing Spring Boot benchmark route failed: $LASTEXITCODE" }
    if ($iteration -gt 2) { $samples.Add($watch.Elapsed.TotalMilliseconds) }
}
$ordered = @($samples | Sort-Object)
$p95Index = [Math]::Ceiling($ordered.Count * 0.95) - 1
[pscustomobject]@{ warmup_runs = 2; measured_runs = 5; p95_ms = [Math]::Round($ordered[$p95Index], 2) } | ConvertTo-Json -Compress
