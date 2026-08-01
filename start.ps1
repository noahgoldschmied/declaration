# Builds and runs the Declaration server as a single jar (API + web client on one port).
# Usage:
#   .\start.ps1              # build + run on port 8090
#   .\start.ps1 -Port 8081   # use a different port
#   .\start.ps1 -SkipBuild   # run the existing jar without rebuilding

param(
    [int]$Port = 8090,
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$serverDir = Join-Path $root "server"

# Prefer a real JAVA_HOME; fall back to the JDK 21 Gradle already provisioned for this project
# (this repo has repeatedly hit a stale/invalid JAVA_HOME and no java on PATH otherwise).
function Resolve-JavaHome {
    if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME "bin\java.exe"))) {
        return $env:JAVA_HOME
    }
    $gradleJdks = Join-Path $env:USERPROFILE ".gradle\jdks"
    if (Test-Path $gradleJdks) {
        $candidate = Get-ChildItem $gradleJdks -Directory |
            Where-Object { $_.Name -match "^eclipse_adoptium-21" } |
            Select-Object -First 1
        if ($candidate) { return $candidate.FullName }
    }
    throw "Could not find a JDK 21 install. Set JAVA_HOME, or run '.\gradlew.bat -v' in server/ once to let Gradle provision one."
}

$javaHome = Resolve-JavaHome
$env:JAVA_HOME = $javaHome
$javaExe = Join-Path $javaHome "bin\java.exe"
Write-Host "Using JAVA_HOME: $javaHome"

Push-Location $serverDir
try {
    if (-not $SkipBuild) {
        Write-Host "Building single deployable jar (embeds the web client)..."
        & .\gradlew.bat bootJar
        if ($LASTEXITCODE -ne 0) { throw "gradlew bootJar failed" }
    }
} finally {
    Pop-Location
}

$jar = Get-ChildItem (Join-Path $serverDir "build\libs") -Filter "server-*.jar" |
    Where-Object { $_.Name -notlike "*-plain.jar" } |
    Select-Object -First 1
if (-not $jar) { throw "No jar found in server\build\libs - run without -SkipBuild first." }

Write-Host ""
Write-Host "Starting Declaration on http://localhost:$Port"
Write-Host ""
& $javaExe -jar $jar.FullName "--server.port=$Port"
