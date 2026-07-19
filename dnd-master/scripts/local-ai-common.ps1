Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Get-LocalAiModelRoot {
    param([Parameter(Mandatory)][string]$ModelRoot, [int]$MinimumFreeGb = 20)

    if ([string]::IsNullOrWhiteSpace($ModelRoot)) { throw 'OLLAMA_MODEL_ROOT is required.' }
    if ($ModelRoot -match '^[A-Za-z0-9_-]+$') { throw 'OLLAMA_MODEL_ROOT must be a host path, not a named volume.' }
    if ($ModelRoot -notmatch '^[EFef]:[\\/]') { throw 'OLLAMA_MODEL_ROOT must be a canonical E: or F: path.' }
    if (-not [IO.Path]::IsPathFullyQualified($ModelRoot)) { throw 'OLLAMA_MODEL_ROOT must be fully qualified.' }
    if (-not (Test-Path -LiteralPath $ModelRoot -PathType Container)) { throw 'OLLAMA_MODEL_ROOT must be an existing directory.' }

    $item = Get-Item -LiteralPath $ModelRoot -Force
    if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { throw 'OLLAMA_MODEL_ROOT must not be a junction or other reparse point.' }
    $canonical = $item.FullName.TrimEnd('\\', '/')
    if ($canonical -notmatch '^[EFef]:\\') { throw 'OLLAMA_MODEL_ROOT resolved outside E: or F:.' }
    $drive = Get-PSDrive -Name $canonical.Substring(0, 1)
    if ($drive.Free -lt ($MinimumFreeGb * 1GB)) { throw "OLLAMA_MODEL_ROOT requires at least $MinimumFreeGb GB free." }
    return $canonical
}

function Get-LocalAiComposeArguments {
    param([Parameter(Mandatory)][string]$ProjectRoot)
    return @('--env-file', (Join-Path $ProjectRoot '.env.local-ai'), '-f', (Join-Path $ProjectRoot 'compose.local-ai.yml'))
}

function Assert-LocalAiTopology {
    param([Parameter(Mandatory)][string]$ProjectRoot)
    $compose = Get-Content -Raw (Join-Path $ProjectRoot 'compose.local-ai.yml')
    foreach ($required in @('network_mode: service:ollama', '127.0.0.1:11434:11434', ':/root/.ollama', 'SPRING_AI_OLLAMA_BASE_URL: http://localhost:11434')) {
        if ($compose -notmatch [regex]::Escape($required)) { throw "Compose topology is missing: $required" }
    }
    if ($compose -match '(?m)^\s*-\s*ollama.*:/root/.ollama') { throw 'Named Ollama model volumes are forbidden.' }
}
