param(
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

function Wait-HealthyContainer {
    param(
        [string]$Name,
        [string]$Service,
        [string]$Url,
        [int]$Attempts = 60
    )

    for ($attempt = 1; $attempt -le $Attempts; $attempt++) {
        docker compose exec --no-TTY $Service curl --fail --silent --max-time 3 $Url | Out-Null
        if ($LASTEXITCODE -eq 0) {
            Write-Host "[PASS] $Name is healthy inside the private network: $Url" -ForegroundColor Green
            return
        }
        Start-Sleep -Seconds 2
    }
    throw "$Name did not become healthy: $Url"
}

docker compose up --build --detach --wait config-server discovery-server gateway
if ($LASTEXITCODE -ne 0) { throw "Docker Compose infrastructure startup failed" }

Wait-HealthyContainer "Config Server" "config-server" "http://localhost:8888/actuator/health/readiness"
Wait-HealthyContainer "Config repository" "config-server" "http://localhost:8888/erudit-gateway/default"
Wait-HealthyContainer "Eureka" "discovery-server" "http://localhost:8761/actuator/health/readiness"
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
Wait-HealthyContainer "Eureka after restart" "discovery-server" "http://localhost:8761/actuator/health/readiness"
Wait-HealthyHttp "Gateway after restart" "$GatewayUrl/actuator/health/readiness"

Write-Host "Infrastructure smoke passed" -ForegroundColor Green
