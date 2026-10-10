@echo off
REM Proves the aisandbox account can build the Android keyboard:
REM every prerequisite must SUCCEED and C:\Users\Acer must stay DENIED.
REM Run:  runas /user:aisandbox "cmd /c C:\AI\projects\keyboard\sandbox-probe.cmd"

echo === identity ===
whoami

echo.
echo === 1. sandbox root (must SUCCEED) ===
dir "C:\AI" >nul 2>&1
if exist "C:\AI\projects\keyboard\gradlew.bat" (echo   OK: project present) else (echo   FAIL: project missing)

echo.
echo === 2. sandbox SDK (must SUCCEED) ===
if exist "C:\AI\tools\android-sdk\platform-tools\adb.exe" (echo   OK: adb.exe) else (echo   FAIL: adb.exe)
if exist "C:\AI\tools\android-sdk\build-tools" (echo   OK: build-tools) else (echo   FAIL: build-tools)
if exist "C:\AI\tools\android-sdk\platforms" (echo   OK: platforms) else (echo   FAIL: platforms)

echo.
echo === 3. JDK from Program Files (must SUCCEED) ===
"C:\Program Files\Android\Android Studio2\jbr\bin\java.exe" -version

echo.
echo === 4. gradle home (must SUCCEED) ===
if exist "C:\AI\artifacts\gradle-home\caches\modules-2" (echo   OK: dependency cache) else (echo   FAIL: dependency cache)
if exist "C:\AI\artifacts\gradle-home\wrapper\dists" (echo   OK: wrapper dists) else (echo   FAIL: wrapper dists)

echo.
echo === 5. Acer profile (must be DENIED - this is the isolation) ===
dir "C:\Users\Acer" >nul 2>&1
if exist "C:\Users\Acer" (echo   LEAK: aisandbox can read C:\Users\Acer - NOT ISOLATED) else (echo   OK: denied)

echo.
echo === 6. write access (must SUCCEED) ===
echo probe > "C:\AI\artifacts\probe-write-test.txt" 2>nul
if exist "C:\AI\artifacts\probe-write-test.txt" (echo   OK: can write to artifacts) else (echo   FAIL: cannot write)
del "C:\AI\artifacts\probe-write-test.txt" >nul 2>&1

echo.
echo === probe complete ===