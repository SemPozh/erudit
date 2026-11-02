param([string]$RegistryUrl = 'http://localhost:8761')
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)

function Wait-Registry([bool]$Present) {
    $deadline = (Get-Date).AddSeconds(120)
    do {
        $matched = $true
        foreach ($name in @('ERUDIT-GATEWAY', 'ERUDIT-AI-PLATFORM')) {
            try {
                $app = Invoke-RestMethod "$RegistryUrl/eureka/apps/$name" -Headers @{Accept='application/json'} -TimeoutSec 5
                $instances = @($app.application.instance)
                $exists = @($instances | Where-Object status -eq 'UP').Count -gt 0
                if ($Present -and $exists) {
                    foreach ($instance in $instances) {
                        if (-not $instance.healthCheckUrl.EndsWith('/actuator/health')) {
                            throw 'Missing health URL'
                        }
                    }
                }
            } catch {
                if ($_.Exception.Response.StatusCode -eq 404) { $exists = $false }
                else { throw }
            }
            if ($exists -ne $Present) { $matched = $false }
        }
        if ($matched) { return }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    throw "Registry did not reach expected presence=$Present"
}

docker compose --profile discovery-smoke up --build -d --wait config-server discovery-server gateway ai-platform
if ($LASTEXITCODE -ne 0) { throw 'Compose startup failed' }
try {
    Wait-Registry $true
    docker compose --profile discovery-smoke stop -t 30 gateway ai-platform
    if ($LASTEXITCODE -ne 0) { throw 'Graceful stop failed' }
    Wait-Registry $false
    Write-Output 'Discovery smoke passed: registration, health URLs and graceful deregistration.'
} finally {
    docker compose --profile discovery-smoke stop -t 30 gateway ai-platform
}
