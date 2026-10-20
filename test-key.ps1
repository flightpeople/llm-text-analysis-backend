# Test a Zhipu (bigmodel) API key directly against the API.
# Guards against the #1 mistake: pasting the MASKED key from the list page.

$uri = "https://open.bigmodel.cn/api/paas/v4/chat/completions"

$key = Read-Host "Paste your Zhipu API Key"
$key = $key.Trim()

if ($key -eq "") {
    Write-Host "ERROR: empty key." -ForegroundColor Red
    exit 1
}
if ($key.Contains("...")) {
    Write-Host ""
    Write-Host "ERROR: This is the MASKED key from the list page, not a real key." -ForegroundColor Red
    Write-Host "The full key is shown ONCE when you create it. Create a new key and copy it from the popup." -ForegroundColor Yellow
    exit 1
}

$body = '{"model":"glm-4-flash","messages":[{"role":"user","content":"Say OK"}]}'

$handler = New-Object System.Net.Http.HttpClientHandler
$handler.UseProxy = $false
$client = New-Object System.Net.Http.HttpClient($handler)
$client.Timeout = [TimeSpan]::FromSeconds(60)

$req = New-Object System.Net.Http.HttpRequestMessage("POST", $uri)
$req.Headers.Authorization = New-Object System.Net.Http.Headers.AuthenticationHeaderValue("Bearer", $key)
$req.Content = New-Object System.Net.Http.StringContent($body, [System.Text.Encoding]::UTF8, "application/json")

Write-Host ""
Write-Host "Calling Zhipu API directly..." -ForegroundColor Cyan

try {
    $resp = $client.SendAsync($req).Result
    $status = [int]$resp.StatusCode
    $text = $resp.Content.ReadAsStringAsync().Result
    Write-Host ""
    Write-Host "HTTP $status" -ForegroundColor $(if ($status -eq 200) { "Green" } else { "Red" })
    Write-Host "Body: $text" -ForegroundColor Gray
    Write-Host ""
    if ($status -eq 200) {
        Write-Host "RESULT: HTTP 200 - THE KEY IS VALID" -ForegroundColor Green
        Write-Host "=> Now re-run start.bat, paste the same full key, then verify.bat." -ForegroundColor Green
    } else {
        Write-Host "RESULT: HTTP $status - THE KEY WAS REJECTED" -ForegroundColor Red
        Write-Host "Checklist:" -ForegroundColor Yellow
        Write-Host "  1. Make sure you pasted the FULL key (format: 32chars.4chars, exactly one dot)" -ForegroundColor Yellow
        Write-Host "  2. Real-name verification completed in the console" -ForegroundColor Yellow
        Write-Host "  3. If the key was just created, wait a few minutes and retry" -ForegroundColor Yellow
    }
}
catch {
    Write-Host "REQUEST FAILED: $($_.Exception.Message)" -ForegroundColor Red
}
finally {
    $client.Dispose()
}
