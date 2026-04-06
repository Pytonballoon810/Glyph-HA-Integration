@echo off
setlocal

set "ROOT_DIR=%~dp0"
set "APK_PATH=%ROOT_DIR%app\build\outputs\apk\debug\app-debug.apk"

if /I "%~1"=="--build" goto :build
if /I "%~1"=="-b" goto :build
goto :install

:build
pushd "%ROOT_DIR%" >nul
call "%ROOT_DIR%gradlew.bat" assembleDebug
if errorlevel 1 (
    echo [ERROR] Debug build failed.
    popd >nul
    exit /b 1
)
popd >nul

:install
where adb >nul 2>nul
if errorlevel 1 (
    echo [ERROR] adb not found in PATH.
    echo Install Android platform-tools and ensure adb is available.
    exit /b 1
)

if not exist "%APK_PATH%" (
    echo [ERROR] APK not found at:
    echo %APK_PATH%
    echo Run this script with --build to generate it first.
    exit /b 1
)

echo [INFO] Checking connected devices...
for /f "skip=1 tokens=1" %%D in ('adb devices') do (
    if not "%%D"=="" if not "%%D"=="List" set "HAS_DEVICE=1"
)
if not defined HAS_DEVICE (
    echo [ERROR] No connected Android device detected.
    exit /b 1
)

echo [INFO] Installing debug APK...
adb install -r "%APK_PATH%"
if errorlevel 1 (
    echo [ERROR] Install failed.
    exit /b 1
)

echo [INFO] Launching app...
adb shell monkey -p it.pytonballoon810.glyphha -c android.intent.category.LAUNCHER 1 >nul
if errorlevel 1 (
    echo [WARN] Launch command returned a non-zero exit code.
)

echo [INFO] Done.
exit /b 0
