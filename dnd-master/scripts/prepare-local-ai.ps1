param([int]$MinimumFreeGb = 20)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
. (Join-Path $PSScriptRoot 'local-ai-common.ps1')

if (-not (Test-Path (Join-Path $root '.env.local-ai'))) { throw 'Create .env.local-ai from .env.local-ai.example first.' }
Get-Content (Join-Path $root '.env.local-ai') | Where-Object { $_ -match '^OLLAMA_MODEL_ROOT=' } | ForEach-Object { $env:OLLAMA_MODEL_ROOT = $_.Substring('OLLAMA_MODEL_ROOT='.Length) }
$env:OLLAMA_MODEL_ROOT = Get-LocalAiModelRoot -ModelRoot $env:OLLAMA_MODEL_ROOT -MinimumFreeGb $MinimumFreeGb
$compose = Get-LocalAiComposeArguments -ProjectRoot $root
Assert-LocalAiTopology -ProjectRoot $root
& docker compose @compose up -d ollama
if ($LASTEXITCODE -ne 0) { throw "docker compose up failed: $LASTEXITCODE" }
foreach ($model in @('qwen3:4b-instruct-2507-q4_K_M', 'qwen3-embedding:0.6b')) {
    & docker compose @compose exec ollama ollama pull $model
    if ($LASTEXITCODE -ne 0) { throw "Explicit prepare pull failed for ${model}: $LASTEXITCODE" }
}
Write-Host 'Local AI models prepared. Runtime, verify, and benchmark never pull models.'
