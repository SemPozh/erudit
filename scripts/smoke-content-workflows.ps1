param([string]$BaseUrl = "http://localhost:8089")
$ErrorActionPreference = "Stop"
function Assert-True($condition,$message) { if (-not $condition) { throw $message } }
function Expect-Status($code,[scriptblock]$request) {
    try { $null = & $request; throw "Expected HTTP $code" }
    catch { if ([int]$_.Exception.Response.StatusCode -ne $code) { throw } }
}
$ready = $false
for ($attempt=0;$attempt -lt 60;$attempt++) {
    try { $null=Invoke-RestMethod "$BaseUrl/api/v1/admin/content/moderation" -TimeoutSec 10; $ready=$true; break }
    catch { Start-Sleep -Seconds 2 }
}
Assert-True $ready "Gateway has not discovered the content workflow API"
$draft = @{
    categoryId="10000000-0000-0000-0000-000000000001"; type="ARTICLE"; title="Workflow smoke $([guid]::NewGuid())"
    description="Local workflow fixture"; body="Light is scattered. Blue light scatters more."
    difficulty="BEGINNER"; estimatedMinutes=2; premiumLocked=$false; tags=@("workflow-smoke")
} | ConvertTo-Json
$created=Invoke-RestMethod "$BaseUrl/api/v1/content" -Method Post -ContentType "application/json" -Body $draft
$id=$created.data.id
Assert-True ($created.data.status -eq "PENDING_MODERATION") "New content skipped moderation"
Expect-Status 404 { Invoke-RestMethod "$BaseUrl/api/v1/content/$id" }
$approved=Invoke-RestMethod "$BaseUrl/api/v1/admin/content/$id/approve" -Method Post
Assert-True ($approved.data.status -eq "PUBLISHED") "Moderation did not publish content"
Expect-Status 409 { Invoke-RestMethod "$BaseUrl/api/v1/admin/content/$id/reject" -Method Post }
$cards=Invoke-RestMethod "$BaseUrl/api/v1/content/$id/quiz-cards"
Assert-True ($cards.data.Count -eq 2) "Source quiz cards are missing"
$null=Invoke-RestMethod "$BaseUrl/api/v1/content/$id/view" -Method Post
$null=Invoke-RestMethod "$BaseUrl/api/v1/content/$id/complete" -Method Post
$replay=Invoke-RestMethod "$BaseUrl/api/v1/content/$id/view" -Method Post
Assert-True ($replay.data.status -eq "COMPLETED") "Viewing downgraded completed progress"
$feed=Invoke-WebRequest "$BaseUrl/api/v1/content/feed?size=1"
Assert-True ($null -ne $feed.Headers["X-Has-More"] -and $null -eq $feed.Headers["X-Total-Count"]) "Feed has incorrect pagination headers"
$report=Invoke-RestMethod "$BaseUrl/api/v1/content/$id/report" -Method Post -ContentType "application/json" -Body '{"reason":"Smoke reported factual error"}'
$decision='{"decision":"ACCEPT","comment":"Local smoke resolution"}'
$null=Invoke-RestMethod "$BaseUrl/api/v1/admin/content/reports/$($report.data)/resolve" -Method Post -ContentType "application/json" -Body $decision
Expect-Status 409 { Invoke-RestMethod "$BaseUrl/api/v1/admin/content/reports/$($report.data)/resolve" -Method Post -ContentType "application/json" -Body $decision }
Expect-Status 404 { Invoke-RestMethod "$BaseUrl/api/v1/content/$id" }
Expect-Status 404 { Invoke-RestMethod "$BaseUrl/internal/v1/content/moderation" }
Write-Output "Content workflow smoke passed: moderation, quiz cards, progress, infinite feed, report resolution and internal API isolation. Fixture is archived."
