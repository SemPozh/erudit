param([string]$BaseUrl='http://localhost:18089',[string]$Project='erudit-demo')
$ErrorActionPreference='Stop'
if($Project -notmatch '^erudit-demo(?:-[a-z0-9]+)?$'){throw 'Select a disposable demo project'}
$uri=[uri]$BaseUrl
if($uri.Host -notin @('localhost','127.0.0.1')){throw 'Acceptance diagnostics require a local demo'}
$repoRoot=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$composeArgs=@('compose','--project-directory',$repoRoot,'-p',$Project,'-f',"$repoRoot/docker-compose.yml",'-f',"$repoRoot/docker-compose.demo.yml",'--profile','demo')
$ports=& docker @composeArgs port gateway 8080
if($LASTEXITCODE -ne 0 -or -not @($ports|Where-Object{$_.EndsWith(":$($uri.Port)")}).Count){throw 'Gateway URL/project mismatch'}
function Read-Diagnostic([string]$service,[string]$url){
    $raw=& docker @composeArgs exec -T $service curl -fsS --max-time 5 -H 'Accept: application/json' $url
    if($LASTEXITCODE -ne 0){throw "Diagnostic unavailable: $service $url"}
    return ($raw -join "`n")|ConvertFrom-Json -AsHashtable
}
$owners=[ordered]@{
    'config-server'='erudit-config-server';'discovery-server'='erudit-discovery-server';'gateway'='erudit-gateway'
    'user-service'='erudit-user';'content-service'='erudit-content';'quiz-service'='erudit-quiz'
    'event-receiver'='erudit-event-receiver';'ai-platform'='erudit-ai-platform';'notificator'='erudit-notificator'
    'payment-service'='erudit-payment';'admin-service'='erudit-admin'
}
$results=[Collections.Generic.List[object]]::new()
try{
    $expected=@($owners.Values|Where-Object{$_ -ne 'erudit-discovery-server'}|ForEach-Object{$_.ToUpperInvariant()})
    $ready=$false
    for($attempt=0;$attempt -lt 90;$attempt++){
        $registry=Read-Diagnostic 'discovery-server' 'http://localhost:8761/eureka/apps'
        $up=@($registry.applications.application|Where-Object{@($_.instance|Where-Object status -eq 'UP').Count -gt 0}|ForEach-Object{$_.name})
        if(@($expected|Where-Object{$_ -notin $up}).Count -eq 0){$ready=$true;break}
        Start-Sleep -Seconds 2
    }
    if(-not $ready){throw 'Not all 10 Eureka clients are UP'}
    foreach($entry in $owners.GetEnumerator()){
        $record=[ordered]@{service=$entry.Key;application=$entry.Value;registry='FAIL';config='FAIL'}
        $results.Add($record)
        if($entry.Key -eq 'discovery-server'){
            $health=Read-Diagnostic $entry.Key 'http://localhost:8761/actuator/health/readiness'
            if($health.status -ne 'UP'){throw 'Registry is unhealthy'}
            $record.registry='PASS: standalone registry (self-registration disabled)'
        }else{$record.registry='PASS: UP in Eureka'}
        if($entry.Key -eq 'config-server'){
            $environment=Read-Diagnostic $entry.Key 'http://localhost:8888/erudit-gateway/default'
            if(-not @($environment.propertySources|Where-Object{$_.name -match 'config-repository'}).Count){throw 'Native config repository not loaded'}
            $record.config='PASS: native bootstrap repository'
        }else{
            $environment=Read-Diagnostic 'config-server' "http://localhost:8888/$($entry.Value)/default"
            if(-not $environment.propertySources.Count){throw "No configuration for $($entry.Value)"}
            $logs=& docker @composeArgs logs --no-color $entry.Key
            if($LASTEXITCODE -ne 0){throw "Cannot read config-client startup evidence: $($entry.Key)"}
            if(-not ($logs -match [regex]::Escape("Located environment: name=$($entry.Value),"))){throw "No successful Config Client import: $($entry.Value)"}
            $record.config='PASS: Config Client import + nonempty Config Server environment'
        }
        Write-Host "[PASS] $($entry.Value): $($record.registry); $($record.config)"
    }
    & (Join-Path $PSScriptRoot 'smoke-swagger.ps1') -BaseUrl $BaseUrl
    if($LASTEXITCODE -ne 0){throw 'Swagger acceptance failed'}
}finally{
    $null=New-Item -ItemType Directory (Join-Path $repoRoot 'build') -Force
    $results.ToArray()|ConvertTo-Json -Depth 6|Set-Content (Join-Path $repoRoot 'build/acceptance-platform.json')
}
Write-Output 'Platform acceptance passed: 11 applications, 10 Eureka clients + standalone registry, Config imports/native bootstrap, Gateway Swagger.'
