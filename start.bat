@echo off
chcp 65001 >nul
set "JAVA_HOME=C:\Program Files\Java\jdk-21.0.12.1"
if not defined LLM_API_KEY (
    set /p "LLM_API_KEY=Enter your Zhipu API Key: "
)
if not defined LLM_API_KEY (
    echo.
    echo ERROR: API key is empty. Get one at open.bigmodel.cn, then run this again.
    echo.
    pause
    exit /b 1
)
echo.
echo Starting LLM Text Analysis service on http://localhost:8080
echo Keep this window open. Press Ctrl+C to stop.
echo.
call mvnw.cmd spring-boot:run
echo.
echo Service stopped. Press any key to close.
pause >nul
