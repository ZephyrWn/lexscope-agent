@echo off
setlocal

title LexScope Agent Launcher

set "ROOT=%~dp0"
cd /d "%ROOT%"

echo.
echo ========================================
echo  LexScope Agent - One Click Startup
echo ========================================
echo.
echo Project directory:
echo %CD%
echo.

if not exist "%ROOT%scripts\start_windows.ps1" (
    echo [error] scripts\start_windows.ps1 was not found.
    echo Please make sure this launcher is in the project root directory.
    echo.
    pause
    exit /b 1
)

powershell -NoProfile -ExecutionPolicy Bypass -File "%ROOT%scripts\start_windows.ps1"
set "EXIT_CODE=%ERRORLEVEL%"

echo.
if not "%EXIT_CODE%"=="0" (
    echo [error] LexScope Agent startup failed. Exit code: %EXIT_CODE%
    echo Check the messages above. Docker Desktop usually needs to be running first.
) else (
    echo [ok] LexScope Agent startup command completed.
    echo.
    echo Frontend: http://localhost:8088/
    echo Backend:  http://localhost:8080/
    echo Swagger:  http://localhost:8080/swagger-ui/index.html
)

echo.
echo Press any key to close this window.
pause >nul
exit /b %EXIT_CODE%
