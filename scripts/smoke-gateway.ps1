param([string]$GatewayUrl = 'http://localhost:8089', [string]$RegistryUrl = 'http://localhost:8761')
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
docker compose --profile gateway-smoke up --build -d --wait config-server discovery-server gateway gateway-echo
if ($LASTEXITCODE -ne 0) { throw 'Compose startup failed' }
$instanceId = 'gateway-smoke-' + [guid]::NewGuid().ToString()
$registration = @{instance=@{instanceId=$instanceId; app='ERUDIT-CONTENT'; hostName='gateway-echo';
    vipAddress='erudit-content'; secureVipAddress='erudit-content';
    ipAddr='127.0.0.1'; status='UP'; port=@{'$'=8000;'@enabled'='true'};
    dataCenterInfo=@{'@class'='com.netflix.appinfo.InstanceInfo$DefaultDataCenterInfo';name='MyOwn'};
    healthCheckUrl='http://gateway-echo:8000/health'}} | ConvertTo-Json -Depth 6
try {
    Invoke-WebRequest "$RegistryUrl/eureka/apps/ERUDIT-CONTENT" -Method Post -ContentType 'application/json' -Body $registration | Out-Null
    $ready = $false
    $deadline = (Get-Date).AddSeconds(120)
    do {
        $response = Invoke-WebRequest "$GatewayUrl/api/v1/content?page=2" -Headers @{'X-Request-ID'='gateway-smoke';'X-User-Id'='42'} -SkipHttpErrorCheck
        if ($response.StatusCode -eq 200) { $ready=$true; break }
        if ($response.StatusCode -ne 503) { throw "Unexpected gateway response: $($response.StatusCode)" }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    if (-not $ready) { throw 'Gateway did not discover the registered upstream' }
    $body = $response.Content | ConvertFrom-Json
    if ($body.path -ne '/api/v1/content?page=2' -or $body.requestId -ne 'gateway-smoke' -or
        $body.userId -ne '42' -or $null -ne $body.authorization) { throw 'Path/context forwarding failed' }
    foreach ($path in @('/internal/v1/users','/eureka/apps','/erudit-user/default','/missing')) {
        $response = Invoke-WebRequest "$GatewayUrl$path" -SkipHttpErrorCheck
        if ($response.StatusCode -ne 404 -or ($response.Content | ConvertFrom-Json).error.code -ne 'NOT_FOUND') {
            throw "Private or unknown path was not rejected: $path"
        }
    }
    $response = Invoke-WebRequest "$GatewayUrl/api/v1/payments/webhooks/test" -SkipHttpErrorCheck
    if ($response.StatusCode -ne 503 -or ($response.Content | ConvertFrom-Json).error.code -ne 'SERVICE_UNAVAILABLE') {
        throw 'Missing service did not produce structured 503'
    }
    Write-Output 'Gateway smoke passed: Eureka routing, unchanged path, optional user context, request ID, no auth, 404 and 503.'
} finally {
    Invoke-WebRequest "$RegistryUrl/eureka/apps/ERUDIT-CONTENT/$instanceId" -Method Delete -SkipHttpErrorCheck | Out-Null
    docker compose --profile gateway-smoke stop gateway-echo
}
