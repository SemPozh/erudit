param([string]$BaseUrl="http://localhost:18089",[string]$Project="erudit-demo")
$ErrorActionPreference="Stop"
if($Project -notmatch '^erudit-demo(?:-[a-z0-9]+)?$'){throw 'Dependency stops are restricted to disposable demo projects'}
$uri=[uri]$BaseUrl;if($uri.Host -notin @('localhost','127.0.0.1')){throw 'Only a local demo URL is allowed'}
$repoRoot=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$composeArgs=@('compose','--project-directory',$repoRoot,'-p',$Project,'-f',(Join-Path $repoRoot 'docker-compose.yml'),'-f',(Join-Path $repoRoot 'docker-compose.demo.yml'),'--profile','demo')
$port=& docker @composeArgs port gateway 8080
if($LASTEXITCODE -ne 0 -or -not @($port|Where-Object{$_.EndsWith(":$($uri.Port)")}).Count){throw 'URL does not belong to the selected demo project'}
. (Join-Path $PSScriptRoot 'smoke-demo-contracts.ps1') -BaseUrl $BaseUrl -LibraryOnly -TraceHttp
$catalog=Invoke-RestMethod "$BaseUrl/openapi/swagger-config"
foreach($url in $catalog.urls){$documents+=(Invoke-WebRequest "$BaseUrl$($url.url)").Content|ConvertFrom-Json -AsHashtable -DateKind String}
$owned=[Collections.Generic.List[string]]::new();$results=[Collections.Generic.List[string]]::new()
function Invoke-Dependency([string[]]$Arguments){& docker @composeArgs @Arguments;if($LASTEXITCODE -ne 0){throw "Dependency command failed: $Arguments"}}
function New-ProbeContent{
    $draft=@{categoryId='10000000-0000-0000-0000-000000000001';type='ARTICLE';title='Resilience probe';description='Local fallback test';body='Fallback source fact.';difficulty='BEGINNER';estimatedMinutes=1;premiumLocked=$false;tags=@('resilience')}
    $item=Invoke-Contract POST '/api/v1/content' 201 $draft;$owned.Add($item.data.id)
    $null=Invoke-Contract POST "/api/v1/admin/content/$($item.data.id)/approve" 200
    return Invoke-Contract GET "/api/v1/content/$($item.data.id)/quiz-cards"
}
function Assert-AiState([string]$state){
    $metrics=& docker @composeArgs exec -T content-service curl -fsS http://localhost:8083/actuator/prometheus
    if($LASTEXITCODE -ne 0){throw 'Cannot read circuit diagnostics'}
    $lines=@($metrics|Where-Object{$_ -match '^resilience4j_circuitbreaker_state\{' -and $_ -match 'name="ai"' -and $_ -match "state=`"$state`"" -and $_ -match '\}\s+1(\.0)?$'})
    Assert-Contract ($lines.Count -ge 1) "AI circuit state $state not confirmed by metrics"
}
try{
    Invoke-Dependency @('stop','ai-platform')
    for($i=0;$i -lt 2;$i++){$cards=New-ProbeContent;Assert-Contract ($cards.data[0].question.StartsWith('Which fact')) 'AI failure did not select local Content generator'}
    Assert-AiState 'open';$results.Add('PASS: AI stopped; local cards, circuit OPEN confirmed by Prometheus')
    $timer=[Diagnostics.Stopwatch]::StartNew();$null=New-ProbeContent;$timer.Stop()
    Assert-Contract ($timer.Elapsed.TotalSeconds -lt 5) 'Open-circuit fallback exceeded five seconds'
    $null=Invoke-Contract POST '/api/v1/quizzes' 201 @{title='Fallback quiz';prompt='Local question';options=@('A','B');correctIndex=0;hint='Local hint'}
    $results.Add('PASS: open circuit and Quiz fallback keep writes available')
    $subscription=Invoke-Contract POST '/api/v1/subscriptions' 201 @{plan='PREMIUM_MONTHLY';paymentMethodId='mock_card'}
    $before=Invoke-Contract GET '/api/v1/content/11000000-0000-0000-0000-000000000003'
    Assert-Contract ($before.data.body -ne 'Premium content') 'Premium precondition failed before payment outage'
    Invoke-Dependency @('stop','payment-service')
    $premium=Invoke-Contract GET '/api/v1/content/11000000-0000-0000-0000-000000000003'
    Assert-Contract ($premium.data.body -eq 'Premium content') 'Payment outage exposed premium body'
    $null=Invoke-Contract GET '/api/v1/content/11000000-0000-0000-0000-000000000001'
    $results.Add('PASS: payment stopped; premium fails closed, free content remains available')
    Invoke-Dependency @('start','ai-platform','payment-service')
    Invoke-Dependency @('up','-d','--no-build','--wait','--wait-timeout','180','ai-platform','payment-service')
$recovered=$false
for($i=0;$i -lt 60;$i++){
    $cards=New-ProbeContent
    if(-not $cards.data[0].question.StartsWith('Which fact')){$recovered=$true;break}
    Start-Sleep -Seconds 2
}
Assert-Contract $recovered 'AI did not recover after dependency restart'
$null=New-ProbeContent;Assert-AiState 'closed'
$results.Add('PASS: AI recovery; circuit CLOSED confirmed by Prometheus')

    # Gateway and Content have independent Eureka caches after a dependency restart.
    $paymentReady=$false
    for($i=0;$i -lt 90;$i++){
        try{$null=Invoke-WebRequest "$BaseUrl/api/v1/subscriptions/plans" -TimeoutSec 5;$paymentReady=$true;break}catch{Start-Sleep -Seconds 2}
    }
    Assert-Contract $paymentReady 'Payment route did not recover in Gateway'
    # Content's Feign discovery cache and circuit recover independently of Gateway.
    # A contract-valid masked 200 is expected until Content can resolve payment again.
    $entitlementRecovered=$false
    for($i=0;$i -lt 90;$i++){
        $after=Invoke-Contract GET '/api/v1/content/11000000-0000-0000-0000-000000000003'
        if($after.data.body -ne 'Premium content'){$entitlementRecovered=$true;break}
        Start-Sleep -Seconds 2
    }
    Assert-Contract $entitlementRecovered 'Payment entitlement did not recover in Content within 180 seconds'
    $null=Invoke-Contract POST "/api/v1/subscriptions/$($subscription.data.id)/cancel" 200
    $results.Add('PASS: payment recovery; premium entitlement and Gateway cancellation restored')
}catch{$results.Add('FAIL: '+$_.Exception.Message);throw
}finally{
    Invoke-Dependency @('start','ai-platform','payment-service')
    Invoke-Dependency @('up','-d','--no-build','--wait','--wait-timeout','180','ai-platform','payment-service')
    foreach($id in $owned){try{$null=Invoke-WebRequest "$BaseUrl/api/v1/content/$id" -Method Delete -Headers $headers}catch{Write-Warning "Own probe cleanup failed: $id"}}
    $null=New-Item -ItemType Directory build -Force
    [IO.File]::WriteAllLines((Join-Path (Get-Location) 'build/gateway-resilience.md'),@('# Dependency failure/recovery results','')+$results.ToArray())
}
Write-Output 'Resilience smoke passed: AI OPEN/local fallback, payment masking, recovery and CLOSED circuit.'
