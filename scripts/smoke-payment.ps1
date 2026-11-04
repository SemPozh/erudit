param([string]$BaseUrl = "http://localhost:8089")
$ErrorActionPreference = "Stop"
$demoUser = "00000000-0000-0000-0000-000000000001"
$headers = @{ "X-User-Id" = $demoUser }
$contentId = $null
function Assert-True($condition, $message) { if (-not $condition) { throw $message } }
function Entitlement {
    $json = docker compose exec -T payment-service curl --fail --silent "http://localhost:8087/internal/v1/entitlements/$demoUser"
    if ($LASTEXITCODE -ne 0) { throw "Internal entitlement request failed" }
    return ($json | ConvertFrom-Json)
}
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    $ready = $false
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        try {
            $plans = Invoke-RestMethod "$BaseUrl/api/v1/subscriptions/plans" -TimeoutSec 10
            $ready = $true
            break
        } catch { Start-Sleep -Seconds 2 }
    }
    Assert-True $ready "Gateway did not discover payment service"
    Assert-True ($plans.data.code -contains "PREMIUM_MONTHLY") "Demo tariff is missing"
    try {
        $current = Invoke-RestMethod "$BaseUrl/api/v1/subscriptions/me" -Headers $headers
        if ($current.data.status -eq "ACTIVE") {
            $null = Invoke-RestMethod "$BaseUrl/api/v1/subscriptions/$($current.data.id)/cancel" -Method Post -Headers $headers
        }
    } catch {
        if ([int]$_.Exception.Response.StatusCode -ne 404) { throw }
    }
    $body = @{ plan = "PREMIUM_MONTHLY"; paymentMethodId = "mock_card" } | ConvertTo-Json
    $created = Invoke-RestMethod "$BaseUrl/api/v1/subscriptions" -Method Post -Headers $headers -ContentType "application/json" -Body $body
    Assert-True ($created.data.status -eq "ACTIVE") "Subscription was not activated"
    Assert-True (Entitlement).premium "Premium entitlement was not granted"
    $draft = @{
        categoryId = "10000000-0000-0000-0000-000000000001"; type = "ARTICLE"
        title = "Payment smoke premium"; description = "Payment smoke description"; body = "Payment smoke body"
        difficulty = "BEGINNER"; estimatedMinutes = 5; premiumLocked = $true; tags = @("payment-smoke")
    } | ConvertTo-Json
    $item = Invoke-RestMethod "$BaseUrl/api/v1/content" -Method Post -Headers $headers -ContentType "application/json" -Body $draft
    $contentId = $item.data.id
    $null = Invoke-RestMethod "$BaseUrl/api/v1/admin/content/$contentId/approve" -Method Post -Headers $headers
    $visible = Invoke-RestMethod "$BaseUrl/api/v1/content/$contentId" -Headers $headers
    Assert-True ($visible.data.body -eq "Payment smoke body") "Content did not receive the paid entitlement through Feign"
    $history = Invoke-RestMethod "$BaseUrl/api/v1/subscriptions/payments?size=1" -Headers $headers
    Assert-True ($history.data[0].status -eq "PAID") "Paid history entry is missing"
    $cancelled = Invoke-RestMethod "$BaseUrl/api/v1/subscriptions/$($created.data.id)/cancel" -Method Post -Headers $headers
    Assert-True ($cancelled.data.status -eq "EXPIRED") "Subscription was not cancelled"
    Assert-True (-not (Entitlement).premium) "Premium remained active after cancellation"
    $locked = Invoke-RestMethod "$BaseUrl/api/v1/content/$contentId" -Headers $headers
    Assert-True ($locked.data.body -eq "Premium content") "Content remained visible after cancellation"
    try {
        $null = Invoke-RestMethod "$BaseUrl/api/v1/subscriptions" -Method Post -Headers @{ "X-User-Id" = [guid]::NewGuid().ToString() } -ContentType "application/json" -Body $body
        throw "Missing user was accepted"
    } catch {
        if ([int]$_.Exception.Response.StatusCode -ne 404) { throw }
    }
    try {
        $null = Invoke-RestMethod "$BaseUrl/internal/v1/entitlements/$demoUser"
        throw "Internal API is exposed through Gateway"
    } catch {
        if ([int]$_.Exception.Response.StatusCode -ne 404) { throw }
    }
    Write-Output "Payment smoke passed: plans, purchase, real Feign user lookup, history, content entitlement through Feign, cancellation and internal API isolation."
} finally {
    try {
        if ($contentId) { $null = Invoke-RestMethod "$BaseUrl/api/v1/content/$contentId" -Method Delete -Headers $headers }
    } finally { Pop-Location }
}
