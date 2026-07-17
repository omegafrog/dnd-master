param([int]$StartupTimeoutSeconds = 90)

$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$modules = 'identity-access-service,adventure-service,rule-knowledge-service,character-management-service,dice-roll-service,combat-map-service,ai-game-master-service'
$manifest = $null

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
        $version = (& $java --version | Select-Object -First 1) -join ''
        if ($version -match '^(openjdk|java) 21[\.]') {
            $env:JAVA_HOME = $candidate
            return
        }
    }
    throw 'Java 21 JAVA_HOME을 찾을 수 없습니다.'
}

function Stop-ProcessTree([int]$ProcessId) {
    if (Get-Process -Id $ProcessId -ErrorAction SilentlyContinue) {
        & taskkill.exe /PID $ProcessId /T /F 2>&1 | Out-Null
    }
}

function Assert-Endpoint([string]$Name, [string]$Url, [string]$ExpectedText) {
    $deadline = [DateTime]::UtcNow.AddSeconds($StartupTimeoutSeconds)
    do {
        try {
            $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 3
            $content = if ($response.Content -is [byte[]]) {
                [System.Text.Encoding]::UTF8.GetString($response.Content)
            } else {
                [string]$response.Content
            }
            if ($response.StatusCode -eq 200 -and $content -match $ExpectedText) {
                Write-Host "PASS $Name $Url"
                return
            }
        } catch {
            Start-Sleep -Milliseconds 400
        }
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "endpoint 검증 실패: $Name $Url"
}

try {
    Use-Java21
    & (Join-Path $root 'mvnw.cmd') -f (Join-Path $root 'pom.xml') -pl $modules -am test '-Dtest=OpenApiIntegrationTest'
    if ($LASTEXITCODE -ne 0) { throw "OpenAPI 통합 테스트 실패: exit $LASTEXITCODE" }

    & (Join-Path $root 'mvnw.cmd') -f (Join-Path $root 'pom.xml') -pl $modules -am package '-DskipTests'
    if ($LASTEXITCODE -ne 0) { throw "서비스 패키징 실패: exit $LASTEXITCODE" }

    if (-not (Test-Path (Join-Path $root 'web-ui/node_modules'))) {
        & npm.cmd --prefix (Join-Path $root 'web-ui') install --no-package-lock
        if ($LASTEXITCODE -ne 0) { throw "UI 의존성 설치 실패: exit $LASTEXITCODE" }
    }

    $manifest = & (Join-Path $PSScriptRoot 'start-local.ps1') -RootPath $root -SkipBuild
    foreach ($entry in $manifest.Processes | Where-Object Name -Ne 'web-ui') {
        $base = "http://127.0.0.1:$($entry.Port)"
        Assert-Endpoint "$($entry.Name) health" "$base/actuator/health" '"status":"UP"'
        Assert-Endpoint "$($entry.Name) OpenAPI" "$base/v3/api-docs" '"openapi"'
        Assert-Endpoint "$($entry.Name) Swagger UI" "$base/swagger-ui/index.html" 'Swagger UI'
    }
    Assert-Endpoint 'web-ui root' 'http://127.0.0.1:15173/' 'D&amp;D Master|id="root"'
    Write-Host 'Runtime verification passed.'
} catch {
    if ($manifest -and $manifest.LogDirectory) {
        Write-Host "Runtime logs: $($manifest.LogDirectory)"
        Get-ChildItem $manifest.LogDirectory -Filter '*.log' | ForEach-Object {
            Write-Host "--- $($_.Name)"
            Get-Content $_.FullName -Tail 30
        }
    }
    throw
} finally {
    if ($manifest) {
        $entries = @($manifest.Processes)
        for ($index = $entries.Count - 1; $index -ge 0; $index--) {
            Stop-ProcessTree $entries[$index].Process.Id
        }
    }
}
