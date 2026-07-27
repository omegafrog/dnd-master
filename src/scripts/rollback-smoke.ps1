param([int]$TimeoutSeconds = 240)

$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent

function Use-Java21 {
    $candidates = [System.Collections.Generic.List[string]]::new()
    if ($env:JAVA_HOME) { $candidates.Add($env:JAVA_HOME) }
    $localJdks = Join-Path $HOME '.jdks'
    if (Test-Path $localJdks) {
        Get-ChildItem $localJdks -Directory | Where-Object Name -Match '21' | ForEach-Object { $candidates.Add($_.FullName) }
    }
    foreach ($candidate in $candidates) {
        $java = Join-Path $candidate 'bin/java.exe'
        if (-not (Test-Path $java)) { continue }
        if ((& $java --version | Select-Object -First 1) -match '^(openjdk|java) 21[\.]') {
            $env:JAVA_HOME = $candidate
            return
        }
    }
    throw 'Java 21 JAVA_HOME을 찾을 수 없습니다.'
}

Use-Java21
$v1 = Join-Path $root 'infra/migrations/compatibility/V1__baseline_domain_state.sql'
$v2 = Join-Path $root 'infra/migrations/compatibility/V2__expand_and_rebuild_vector_index.sql'
if (-not (Test-Path $v1) -or -not (Test-Path $v2)) { throw '호환성 migration 파일이 없습니다.' }
$v2Text = Get-Content -Raw -Encoding UTF8 $v2
if ($v2Text -notmatch 'ADD COLUMN context_json JSONB' -or
    $v2Text -notmatch 'compat_rulebook_vector_v2' -or
    $v2Text -notmatch 'compat_rulebook_search_current') {
    throw 'expand migration 또는 vector rebuild 계약이 누락됐습니다.'
}

$process = Start-Process -FilePath (Join-Path $root 'mvnw.cmd') -ArgumentList @(
        '-f', ('"{0}"' -f (Join-Path $root 'pom.xml')), '-pl', 'system-tests', '-am', 'test',
        '-Dtest=MigrationCompatibilityTest') -WorkingDirectory (Split-Path $root -Parent) `
    -PassThru -NoNewWindow
$null = $process.Handle
if (-not $process.WaitForExit($TimeoutSeconds * 1000)) {
    & taskkill.exe /PID $process.Id /T /F 2>&1 | Out-Null
    throw "rollback smoke timeout: ${TimeoutSeconds}s"
}
$process.WaitForExit()
$process.Refresh()
if ($process.ExitCode -ne 0) { throw "rollback smoke 실패: exit $($process.ExitCode)" }
Write-Host 'Rollback compatibility smoke passed.'
