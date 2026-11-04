param([string]$BaseUrl="http://localhost:8089",[switch]$SkipDiscoveryWarmup)
$ErrorActionPreference="Stop"
$actor=[guid]::NewGuid().ToString();$trace="admin-smoke-$([guid]::NewGuid())"
$headers=@{"X-User-Id"=$actor;"X-Request-ID"=$trace}
function Assert-True($condition,$message){if(-not $condition){throw $message}}
$ready=$false
for($attempt=0;$attempt -lt 60;$attempt++){
    try{
        $null=Invoke-RestMethod "$BaseUrl/api/v1/admin/users" -Headers $headers -TimeoutSec 10
        $null=Invoke-RestMethod "$BaseUrl/api/v1/admin/content/moderation" -Headers $headers -TimeoutSec 10
        $null=Invoke-RestMethod "$BaseUrl/api/v1/notifications/templates/40000000-0000-0000-0000-000000000001" -TimeoutSec 10
        $ready=$true;break
    }catch{Start-Sleep -Seconds 2}
}
Assert-True $ready "Admin or its service dependencies are not registered"
if(-not $SkipDiscoveryWarmup){Start-Sleep -Seconds 35}
$userId=$null;$contentId=$null
try{
    $user=Invoke-RestMethod "$BaseUrl/api/v1/users/register" -Method Post -ContentType "application/json" -Body (@{email="$([guid]::NewGuid())@erudit.local";displayName="Admin smoke recipient"}|ConvertTo-Json)
    $userId=$user.id
    Assert-True ([bool]$userId) "Registration did not return an ID"
    $draft=@{categoryId="10000000-0000-0000-0000-000000000001";type="ARTICLE";title="Admin smoke fixture";description="Admin facade fixture";body="Source fact.";difficulty="BEGINNER";estimatedMinutes=1;premiumLocked=$false;tags=@("admin-smoke")}|ConvertTo-Json
    $created=Invoke-RestMethod "$BaseUrl/api/v1/content" -Method Post -ContentType "application/json" -Body $draft
    $contentId=$created.data.id
    $approved=Invoke-RestMethod "$BaseUrl/api/v1/admin/content/$contentId/approve" -Method Post -Headers $headers
    Assert-True ($approved.data.status -eq "PUBLISHED") "Admin did not moderate through Content Feign"
    $report=Invoke-RestMethod "$BaseUrl/api/v1/content/$contentId/report" -Method Post -ContentType "application/json" -Body '{"reason":"Admin smoke reported factual error"}'
    $null=Invoke-RestMethod "$BaseUrl/api/v1/admin/content/reports/$($report.data)/resolve" -Method Post -Headers $headers -ContentType "application/json" -Body '{"decision":"ACCEPT","comment":"Admin smoke"}'
    $sent=Invoke-RestMethod "$BaseUrl/api/v1/admin/notifications/send-many" -Method Post -Headers $headers -ContentType "application/json" -Body (@{userIds=@("00000000-0000-0000-0000-000000000001",$userId);templateId="40000000-0000-0000-0000-000000000001"}|ConvertTo-Json)
    Assert-True ($sent.data.count -eq 2) "Local mock batch was not queued"
    $null=Invoke-RestMethod "$BaseUrl/api/v1/admin/users/00000000-0000-0000-0000-000000000001/payments" -Headers $headers
    try{$null=Invoke-RestMethod "$BaseUrl/api/v1/admin/users/00000000-0000-0000-0000-000000000001/subscription" -Headers $headers}catch{if([int]$_.Exception.Response.StatusCode -ne 404){throw}}
    $null=Invoke-RestMethod "$BaseUrl/api/v1/admin/users/$userId/block" -Method Post -Headers $headers
    $found=Invoke-RestMethod "$BaseUrl/api/v1/admin/users?query=Admin%20smoke%20recipient" -Headers $headers
    Assert-True (($found.data|Where-Object id -eq $userId).status -eq "BLOCKED") "User service did not save the restriction"
    $audit=Invoke-RestMethod "$BaseUrl/api/v1/admin/audit?actorId=$actor&action=USER_BLOCKED" -Headers $headers
    Assert-True ($audit.data[0].metadata.status -eq "SUCCESS" -and $audit.data[0].metadata.requestId -eq $trace) "Admin audit lost the result or request ID"
    Write-Output "Admin smoke passed: user search/block, Content moderation/reports, mock notification batch, payment views and request-correlated audit."
}finally{
    if($contentId){$null=Invoke-RestMethod "$BaseUrl/api/v1/content/$contentId" -Method Delete}
    if($userId){$null=Invoke-RestMethod "$BaseUrl/api/v1/users/$userId" -Method Delete}
}
