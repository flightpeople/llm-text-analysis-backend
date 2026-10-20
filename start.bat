@echo off
chcp 65001 >nul
set "JAVA_HOME=C:\Program Files\Java\jdk-21.0.12.1"
if not defined LLM_API_KEY (
    set /p "LLM_API_KEY=Enter your Zhipu API Key: "
)
echo.
echo Starting LLM Text Analysis service on http://localhost:8080
echo Press Ctrl+C to stop.
echo.
call mvnw.cmd spring-boot:run
echo.
echo Service stopped. Press any key to close.
pause >nul
