# Test a Zhipu (bigmodel) API key directly against the API.
# Purpose: separate KEY problems from APP problems.
# If this returns 200, the key is valid and the issue is how it is passed to the app.

$uri = "https://open.bigmodel.cn/api/paas/v4/chat/completions"

$key = Read-Host "Paste your Zhipu API Key"
$key = $key.Trim()

if ($key -eq "") {
    Write-Host "ERROR: empty key, nothing to test." -ForegroundColor Red
    exit 1
}

$body = @{
    model    = "glm-4-flash"
    messages = @(@{ role = "user"; content = "Say OK" })
} | ConvertTo-Json -Depth 5

$headers = @{ Authorization = "Bearer $key" }

Write-Host ""
Write-Host "Calling Zhipu API directly with your key..." -ForegroundColor Cyan

try {
    $r = Invoke-RestMethod -Uri $uri -Method Post -Headers $headers -ContentType "application/json" -Body $body -TimeoutSec 60
    Write-Host ""
    Write-Host "RESULT: HTTP 200 - THE KEY IS VALID" -ForegroundColor Green
    Write-Host "Model replied: $($r.choices[0].message.content)" -ForegroundColor Green
    Write-Host ""
    Write-Host "=> The key works. The app 401 was an env var problem: re-run start.bat and paste the key when prompted." -ForegroundColor Green
}
catch {
    $resp = $_.Exception.Response
    $status = 0
    if ($resp) { $status = [int]$resp.StatusCode }
    Write-Host ""
    Write-Host "RESULT: HTTP $status - THE KEY WAS REJECTED" -ForegroundColor Red
    if ($resp) {
        try {
            $stream = $resp.GetResponseStream()
            $reader = New-Object System.IO.StreamReader($stream)
            $errBody = $reader.ReadToEnd()
            Write-Host "Zhipu says: $errBody" -ForegroundColor Yellow
        }
        catch {}
    }
    Write-Host ""
    Write-Host "Checklist:" -ForegroundColor Yellow
    Write-Host "  1. Key copied completely, no leading/trailing spaces" -ForegroundColor Yellow
    Write-Host "  2. Key created on the API Keys page of open.bigmodel.cn (not the trial center)" -ForegroundColor Yellow
    Write-Host "  3. Real-name verification completed in the console (required before API calls)" -ForegroundColor Yellow
    Write-Host "  4. If key was just created, wait a few minutes and retry" -ForegroundColor Yellow
}
