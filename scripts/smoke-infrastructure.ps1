param(
    [string]$ConfigServerUrl = "http://127.0.0.1:8888",
    [string]$DiscoveryUrl = "http://127.0.0.1:8761",
    [string]$GatewayUrl = "http://127.0.0.1:8089"
)

$ErrorActionPreference = "Stop"

function Wait-HealthyHttp {
    param([string]$Name, [string]$Url, [int]$Attempts = 60)

    for ($attempt = 1; $attempt -le $Attempts; $attempt++) {
        try {
            $response = Invoke-WebRequest -UseBasicParsing -Uri $Url -TimeoutSec 3
            if ($response.StatusCode -eq 200) {
                Write-Host "[PASS] $Name is healthy: $Url" -ForegroundColor Green
                return
            }
        } catch {
            if ($attempt -eq $Attempts) { throw }
        }
        Start-Sleep -Seconds 2
    }
    throw "$Name did not become healthy: $Url"
}

docker compose up --build --detach --wait config-server discovery-server gateway
if ($LASTEXITCODE -ne 0) { throw "Docker Compose infrastructure startup failed" }

Wait-HealthyHttp "Config Server" "$ConfigServerUrl/actuator/health/readiness"
Wait-HealthyHttp "Config repository" "$ConfigServerUrl/erudit-gateway/default"
Wait-HealthyHttp "Eureka" "$DiscoveryUrl/actuator/health/readiness"
Wait-HealthyHttp "Gateway" "$GatewayUrl/actuator/health/readiness"

$gatewayContainer = docker compose ps --quiet gateway
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($gatewayContainer)) {
    throw "Gateway container was not found"
}
$gatewayNetworks = docker inspect $gatewayContainer --format '{{json .NetworkSettings.Networks}}'
if ($LASTEXITCODE -ne 0 -or $gatewayNetworks -notmatch 'erudit-edge' -or $gatewayNetworks -notmatch 'erudit-internal') {
    throw "Gateway must be attached to edge and internal networks"
}

docker compose restart discovery-server gateway
if ($LASTEXITCODE -ne 0) { throw "Infrastructure restart failed" }
Wait-HealthyHttp "Eureka after restart" "$DiscoveryUrl/actuator/health/readiness"
Wait-HealthyHttp "Gateway after restart" "$GatewayUrl/actuator/health/readiness"

Write-Host "Infrastructure smoke passed" -ForegroundColor Green
