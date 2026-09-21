@echo off
cd /d "%~dp0"

rem Auto-select exe by CPU arch:
rem   x86 (32-bit) -> capture-proxy-windows-386.exe   (Win7/10 x86)
rem   amd64 (64-bit) -> capture-proxy-windows-amd64.exe (Win7/10/11 x64)
if /i "%PROCESSOR_ARCHITECTURE%"=="x86" (
    if exist "capture-proxy-windows-386.exe" (
        capture-proxy-windows-386.exe
        goto :end
    )
)

if exist "capture-proxy-windows-amd64.exe" (
    capture-proxy-windows-amd64.exe
) else if exist "capture-proxy-windows-386.exe" (
    capture-proxy-windows-386.exe
) else (
    echo [ERROR] capture-proxy exe not found in current directory.
    pause
)

:end
