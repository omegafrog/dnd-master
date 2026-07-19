param([int]$MinimumFreeGb = 20)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
. (Join-Path $PSScriptRoot 'local-ai-common.ps1')
if (-not (Test-Path (Join-Path $root '.env.local-ai'))) { throw 'Create .env.local-ai from .env.local-ai.example first.' }
Get-Content (Join-Path $root '.env.local-ai') | Where-Object { $_ -match '^OLLAMA_MODEL_ROOT=' } | ForEach-Object { $env:OLLAMA_MODEL_ROOT = $_.Substring('OLLAMA_MODEL_ROOT='.Length) }
$env:OLLAMA_MODEL_ROOT = Get-LocalAiModelRoot -ModelRoot $env:OLLAMA_MODEL_ROOT -MinimumFreeGb $MinimumFreeGb
$compose = Get-LocalAiComposeArguments -ProjectRoot $root
Assert-LocalAiTopology -ProjectRoot $root
& docker compose @compose config --quiet
if ($LASTEXITCODE -ne 0) { throw "docker compose config failed: $LASTEXITCODE" }
& docker compose @compose exec ollama ollama list
if ($LASTEXITCODE -ne 0) { throw "Ollama model inspection failed: $LASTEXITCODE" }
& docker compose @compose ps --status running ollama
if ($LASTEXITCODE -ne 0) { throw 'Ollama is not running.' }
Write-Host 'Verified topology, bind contract, health, and existing models without pulling.'
