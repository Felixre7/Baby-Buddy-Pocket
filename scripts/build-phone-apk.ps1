param(
    [Parameter(Mandatory = $true)][string]$DebugKeystore,
    # Public certificate selected for the authorized fresh installation on 2026-09-22.
    [string]$ExpectedSignerSha256 = '9c83e6e34257b011486e5f52a92a3f3081d8dd79faf8bf6295917d2260c5d333'
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$expected = ($ExpectedSignerSha256 -replace ':', '').ToLowerInvariant()
if ($expected -notmatch '^[0-9a-f]{64}$') { throw 'Expected signer must be a SHA-256 certificate fingerprint.' }
$keyPath = (Resolve-Path -LiteralPath $DebugKeystore).Path
if (!(Test-Path -LiteralPath $keyPath -PathType Leaf)) { throw 'Debug keystore must be an existing file.' }
if (!$env:JAVA_HOME) { throw 'Set JAVA_HOME to JDK 17 first.' }

# Standard Android debug credentials only; never create/replace a key here.
$keyInfo = & (Join-Path $env:JAVA_HOME 'bin/keytool.exe') '-J-Duser.language=en' '-J-Duser.country=US' -list -v -keystore $keyPath -alias androiddebugkey -storepass android
if ($LASTEXITCODE -ne 0) { throw 'Could not read the supplied Android debug certificate.' }
$keyMatch = [regex]::Match(($keyInfo -join "`n"), 'SHA256:\s*([0-9A-Fa-f:]+)')
$actual = $keyMatch.Groups[1].Value.Replace(':', '').ToLowerInvariant()
if ($actual -ne $expected) {
    throw "Signing key mismatch. Expected $expected; found $actual. Use the preserved debug.keystore for the target installation. No phone-update build was started."
}

$sdkRoot = $env:ANDROID_HOME
if (!$sdkRoot) { $sdkRoot = $env:ANDROID_SDK_ROOT }
if (!$sdkRoot) { $sdkRoot = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
$buildTools = Get-ChildItem -LiteralPath (Join-Path $sdkRoot 'build-tools') -Directory |
    Where-Object { $_.Name -match '^\d+\.\d+\.\d+$' } |
    Sort-Object { [version]$_.Name } -Descending |
    Select-Object -First 1
if (!$buildTools) { throw 'Install Android SDK Build Tools and set ANDROID_HOME.' }
$apksigner = Join-Path $buildTools.FullName 'apksigner.bat'

$previousKey = $env:BBP_DEBUG_KEYSTORE
try {
    $env:BBP_DEBUG_KEYSTORE = $keyPath
    & (Join-Path $PSScriptRoot 'build.ps1') -Tasks ':app:assembleDebug'
} finally {
    $env:BBP_DEBUG_KEYSTORE = $previousKey
}

$apk = Join-Path $projectRoot 'app\build\outputs\apk\debug\app-debug.apk'
$verification = & $apksigner verify --print-certs $apk
if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed; do not distribute this build.' }
$apkMatch = [regex]::Match(($verification -join "`n"), 'Signer #1 certificate SHA-256 digest:\s*([0-9A-Fa-f]+)')
if ($apkMatch.Groups[1].Value.ToLowerInvariant() -ne $expected) {
    throw 'Built APK certificate does not match the required signer; do not distribute this build.'
}
Write-Output "Verified debug-signed APK: $apk"
Write-Output "Signer SHA-256: $expected"
Write-Output 'Subsequent updates require the installed app to use this same certificate.'
