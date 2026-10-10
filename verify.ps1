# Gate for the Android keyboard inside the C:\AI sandbox.
#
# Everything it touches is under C:\AI: the SDK copy (C:\AI\tools\android-sdk),
# the gradle home (C:\AI\artifacts\gradle-home) and the log dir. It must NOT
# read anything under C:\Users\Acer - the aisandbox account cannot see that
# path anyway, which is the isolation this gate relies on.
#
# Intended to be run AS aisandbox (a standard user, no admin needed):
#   runas /user:aisandbox "powershell -ExecutionPolicy Bypass -File C:\AI\projects\keyboard\verify.ps1"
#
# Exit: 0 = green, 1 = failed (never edit this file to make something pass).

$ErrorActionPreference = "Continue"

$projectDir = $PSScriptRoot
$sandboxRoot = "C:\AI"
$androidHome = "$sandboxRoot\tools\android-sdk"
$gradleHome = "$sandboxRoot\artifacts\gradle-home"
$logDir = "$sandboxRoot\artifacts\logs"

# JBR from Program Files is world-readable, so no JDK copy is needed in C:\AI.
$jdk = "C:\Program Files\Android\Android Studio2\jbr"

function Fail($msg) {
    Write-Host "FAIL: $msg" -ForegroundColor Red
    exit 1
}

# --- preconditions ---------------------------------------------------------
if (-not (Test-Path -LiteralPath $androidHome)) { Fail "sandbox SDK missing: $androidHome" }
if (-not (Test-Path -LiteralPath "$androidHome\platform-tools\adb.exe")) { Fail "adb.exe missing from sandbox SDK" }
if (-not (Test-Path -LiteralPath "$jdk\bin\java.exe")) { Fail "JDK (JBR) not readable: $jdk" }
if (-not (Test-Path -LiteralPath $gradleHome)) { Fail "gradle home missing: $gradleHome" }
if (-not (Test-Path -LiteralPath "$projectDir\gradlew.bat")) { Fail "gradlew.bat missing in $projectDir" }

New-Item -ItemType Directory -Path $logDir -Force | Out-Null
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$log = "$logDir\verify-$stamp.txt"

# --- environment -----------------------------------------------------------
$env:JAVA_HOME = $jdk
$env:ANDROID_HOME = $androidHome
$env:ANDROID_SDK_ROOT = $androidHome
$env:GRADLE_USER_HOME = $gradleHome
$env:AI_SANDBOX = $sandboxRoot
$env:AI_PROJECTS = "$sandboxRoot\projects"
$env:AI_ARTIFACTS = "$sandboxRoot\artifacts"

# --- build + unit tests (offline: the sandbox has no network) --------------
Write-Host "gate: :app:testDebugUnitTest :app:assembleDebug (offline)" -ForegroundColor Cyan
Write-Host "log : $log" -ForegroundColor DarkGray

Push-Location $projectDir
& ".\gradlew.bat" --offline --no-daemon :app:testDebugUnitTest :app:assembleDebug *>&1 |
    Tee-Object -FilePath $log
$gradleExit = $LASTEXITCODE
Pop-Location

if ($gradleExit -ne 0) { Fail "gradle exited $gradleExit (see $log)" }

$tail = Get-Content -LiteralPath $log -Tail 40 -ErrorAction SilentlyContinue
if (-not ($tail -match "BUILD SUCCESSFUL")) { Fail "no 'BUILD SUCCESSFUL' in log (see $log)" }

# --- unit test results -----------------------------------------------------
# Absolute paths: this runs after Pop-Location, so relative paths would
# resolve against whatever directory launched the gate.
$results = Join-Path $projectDir "app\build\test-results\testDebugUnitTest"
if (-not (Test-Path -LiteralPath $results)) { Fail "no test results at $results" }

$total = 0; $failed = 0; $errors = 0
Get-ChildItem -LiteralPath $results -Filter "*.xml" | ForEach-Object {
    $suite = ([xml](Get-Content -LiteralPath $_.FullName)).testsuite
    $total   += [int]$suite.tests
    $failed  += [int]$suite.failures
    $errors  += [int]$suite.errors
}

Write-Host ""
Write-Host "tests: $total total, $failed failures, $errors errors" -ForegroundColor Cyan

if ($total -le 0)  { Fail "no tests were executed" }
if ($failed -gt 0) { Fail "$failed test failures" }
if ($errors -gt 0) { Fail "$errors test errors" }

# --- artifact --------------------------------------------------------------
$apk = Join-Path $projectDir "app\build\outputs\apk\debug\app-debug.apk"
if (-not (Test-Path -LiteralPath $apk)) { Fail "APK not produced at $apk" }
$apkTime = (Get-Item -LiteralPath $apk).LastWriteTime
Write-Host "apk  : $apk ($apkTime)" -ForegroundColor Cyan

Write-Host ""
Write-Host "PASS: $total/$total tests green, APK built" -ForegroundColor Green
exit 0