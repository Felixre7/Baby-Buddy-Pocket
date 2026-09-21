param([string[]]$Tasks = @(':app:assembleDebug', ':app:testDebugUnitTest', ':app:lintDebug'))

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$socketDirectory = Join-Path $projectRoot '.tools\tmp'
New-Item -ItemType Directory -Force -Path $socketDirectory | Out-Null
$previousJavaOptions = $env:JAVA_TOOL_OPTIONS
Push-Location $projectRoot
try {
    $env:JAVA_TOOL_OPTIONS = "$previousJavaOptions -Djdk.net.unixdomain.tmpdir=`"$socketDirectory`""
    & (Join-Path $projectRoot 'gradlew.bat') @Tasks --no-daemon --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Gradle failed with exit code $LASTEXITCODE" }
} finally {
    $env:JAVA_TOOL_OPTIONS = $previousJavaOptions
    Pop-Location
}
