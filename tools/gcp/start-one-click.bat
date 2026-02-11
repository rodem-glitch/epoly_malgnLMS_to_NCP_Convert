@echo off
setlocal
set SCRIPT_DIR=%~dp0
powershell -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT_DIR%one-click-setup.ps1"
set EXIT_CODE=%ERRORLEVEL%
if not "%EXIT_CODE%"=="0" (
  echo.
  echo [실패] 원클릭 설정 중 오류가 발생했습니다. 위 로그를 확인해 주세요.
  pause
  exit /b %EXIT_CODE%
)
echo.
echo [완료] 원클릭 설정이 끝났습니다.
pause
endlocal
