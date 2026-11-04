param([string]$BaseUrl = "http://localhost:8089", [string]$WebhookKey = "local-webhook-secret")
$ErrorActionPreference = "Stop"
function Assert-True($condition, $message) { if (-not $condition) { throw $message } }
function Signature($body) {
    $mac = [System.Security.Cryptography.HMACSHA256]::new([Text.Encoding]::UTF8.GetBytes($WebhookKey))
    try { return [BitConverter]::ToString($mac.ComputeHash([Text.Encoding]::UTF8.GetBytes($body))).Replace("-", "").ToLowerInvariant() }
    finally { $mac.Dispose() }
}
$demoId = "00000000-0000-0000-0000-000000008700"
$ready = $false
for ($attempt = 0; $attempt -lt 60; $attempt++) {
    try {
        $current = Invoke-RestMethod "$BaseUrl/api/v1/subscriptions/me" -TimeoutSec 10
        if ($current.data.id -eq $demoId) { $ready = $true; break }
    } catch { }
    Start-Sleep -Seconds 2
}
Assert-True $ready "Fixed demo subscription is missing; use docker-compose.payment-demo.yml"
$renewal = [ordered]@{eventId="demo-renewal-87"; occurredAt="2026-11-04T12:00:00Z"; subscriptionId=$demoId; type="RENEWED"} | ConvertTo-Json -Compress
$signature = Signature $renewal
Assert-True ($signature -eq "4733679dfa15539535a042cc17b59898a29775016d638eca845beb47a1251465") "Swagger signature does not match the documented example"
$historyBefore = Invoke-RestMethod "$BaseUrl/api/v1/subscriptions/payments"
for ($i=0;$i -lt 2;$i++) {
    $null = Invoke-RestMethod "$BaseUrl/api/v1/payments/webhooks/MOCK" -Method Post -Headers @{"X-Signature"=$signature} -ContentType "application/json" -Body $renewal
}
$current = Invoke-RestMethod "$BaseUrl/api/v1/subscriptions/me"
Assert-True ($current.data.status -eq "ACTIVE") "Demo webhook did not activate premium"
$history = Invoke-RestMethod "$BaseUrl/api/v1/subscriptions/payments"
Assert-True ($history.pagination.totalElements -eq $historyBefore.pagination.totalElements + 1) "Webhook replay created another payment; reset the disposable demo database before repeating this script"
try {
    $null = Invoke-RestMethod "$BaseUrl/api/v1/payments/webhooks/MOCK" -Method Post -Headers @{"X-Signature"="wrong"} -ContentType "application/json" -Body $renewal
    throw "Invalid signature was accepted"
} catch { if ([int]$_.Exception.Response.StatusCode -ne 400) { throw } }
$receipt = @{provider="APP_STORE";receipt="monthly-receipt-demo-87"} | ConvertTo-Json -Compress
$first = Invoke-RestMethod "$BaseUrl/api/v1/subscriptions/mobile/verify" -Method Post -ContentType "application/json" -Body $receipt
$null = Invoke-RestMethod "$BaseUrl/api/v1/subscriptions/$($first.data.id)/cancel" -Method Post
$replay = Invoke-RestMethod "$BaseUrl/api/v1/subscriptions/mobile/verify" -Method Post -ContentType "application/json" -Body $receipt
Assert-True ($replay.data.id -eq $first.data.id -and $replay.data.status -eq "EXPIRED") "Receipt replay reactivated a cancelled subscription"
$history = Invoke-RestMethod "$BaseUrl/api/v1/subscriptions/payments"
Assert-True ($history.pagination.totalElements -eq $historyBefore.pagination.totalElements + 2) "Receipt replay created another payment"
Write-Output "Payment events smoke passed: Swagger example, signature rejection, webhook replay, mobile receipt and cancellation replay."
