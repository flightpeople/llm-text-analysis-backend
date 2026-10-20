# Verify script for LLM text analysis service.
# Usage: make sure the server is running (start.bat window open, last line "Started Application"),
# then run verify.bat or: powershell -File verify.ps1

$uri = "http://localhost:8080/api/analysis/summarize"
$body = '{"text":"Spring Boot is a framework designed to simplify the bootstrapping and development of new Spring applications. It provides specific configuration defaults so that developers no longer need to define boilerplate configuration."}'

Write-Host "Testing $uri ..." -ForegroundColor Cyan

try {
    $r = Invoke-RestMethod -Uri $uri -Method Post -ContentType "application/json" -Body $body -TimeoutSec 90
    Write-Host ""
    Write-Host "===== RESULT =====" -ForegroundColor Green
    $r | ConvertTo-Json -Depth 5
    Write-Host ""
    if ($r.code -eq 200) {
        Write-Host "SUCCESS - full chain works with real API key." -ForegroundColor Green
    } else {
        Write-Host "SERVER RESPONDED with code=$($r.code), message=$($r.message)" -ForegroundColor Yellow
    }
} catch {
    Write-Host ""
    Write-Host "CONNECTION FAILED: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "Make sure the server window is still open and its last line says 'Started Application'." -ForegroundColor Yellow
    Write-Host "If not running: double-click start.bat to start it, keep that window open, then run this again." -ForegroundColor Yellow
}
