param(
    [string]$RootPath = (Split-Path $PSScriptRoot -Parent),
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'

$services = @(
    [pscustomobject]@{ Name = 'identity-access-service'; Port = 18081 },
    [pscustomobject]@{ Name = 'adventure-service'; Port = 18082 },
    [pscustomobject]@{ Name = 'rule-knowledge-service'; Port = 18083 },
    [pscustomobject]@{ Name = 'character-management-service'; Port = 18084 },
    [pscustomobject]@{ Name = 'dice-roll-service'; Port = 18085 },
    [pscustomobject]@{ Name = 'combat-map-service'; Port = 18086 },
    [pscustomobject]@{ Name = 'ai-game-master-service'; Port = 18087 }
)

function Stop-ProcessTree([int]$ProcessId) {
    if (Get-Process -Id $ProcessId -ErrorAction SilentlyContinue) {
        & taskkill.exe /PID $ProcessId /T /F 2>&1 | Out-Null
    }
}

if (-not $SkipBuild) {
    $modules = ($services.Name -join ',')
    & (Join-Path $RootPath 'mvnw.cmd') -f (Join-Path $RootPath 'pom.xml') -pl $modules -am package '-DskipTests'
    if ($LASTEXITCODE -ne 0) { throw "서비스 패키징 실패: exit $LASTEXITCODE" }
    if (-not (Test-Path (Join-Path $RootPath 'web-ui/node_modules'))) {
        & npm.cmd --prefix (Join-Path $RootPath 'web-ui') install --no-package-lock
        if ($LASTEXITCODE -ne 0) { throw "UI 의존성 설치 실패: exit $LASTEXITCODE" }
    }
}

$java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { 'java.exe' }
$logDirectory = Join-Path ([System.IO.Path]::GetTempPath()) ("dnd-master-runtime-" + [guid]::NewGuid())
New-Item -ItemType Directory -Path $logDirectory | Out-Null
$started = [System.Collections.Generic.List[object]]::new()

try {
    foreach ($service in $services) {
        $jar = Get-ChildItem (Join-Path $RootPath "$($service.Name)/target") -Filter '*.jar' |
            Where-Object Name -NotLike '*.original' |
            Select-Object -First 1
        if (-not $jar) { throw "실행 JAR 없음: $($service.Name)" }

        $arguments = @('-jar', ('"{0}"' -f $jar.FullName), "--server.port=$($service.Port)")
        if ($service.Name -eq 'identity-access-service') {
            $arguments += @(
                '--spring.main.lazy-initialization=true',
                '--spring.flyway.enabled=false',
                '--spring.datasource.url=jdbc:postgresql://127.0.0.1:1/dnd_master',
                '--management.health.db.enabled=false'
            )
        }
        $process = Start-Process -FilePath $java -ArgumentList $arguments -WorkingDirectory $RootPath -PassThru -WindowStyle Hidden `
            -RedirectStandardOutput (Join-Path $logDirectory "$($service.Name).out.log") `
            -RedirectStandardError (Join-Path $logDirectory "$($service.Name).err.log")
        $started.Add([pscustomobject]@{ Name = $service.Name; Port = $service.Port; Process = $process })
    }

    $uiProcess = Start-Process -FilePath 'npm.cmd' -ArgumentList @('exec', '--', 'vite', '--host', '127.0.0.1', '--port', '15173', '--strictPort') `
        -WorkingDirectory (Join-Path $RootPath 'web-ui') -PassThru -WindowStyle Hidden `
        -RedirectStandardOutput (Join-Path $logDirectory 'web-ui.out.log') `
        -RedirectStandardError (Join-Path $logDirectory 'web-ui.err.log')
    $started.Add([pscustomobject]@{ Name = 'web-ui'; Port = 15173; Process = $uiProcess })

    [pscustomobject]@{ Processes = @($started); LogDirectory = $logDirectory }
} catch {
    for ($index = $started.Count - 1; $index -ge 0; $index--) {
        Stop-ProcessTree $started[$index].Process.Id
    }
    throw
}
