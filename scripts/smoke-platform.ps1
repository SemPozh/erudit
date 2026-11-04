param([string]$ImageProject="erudit-demo",[int]$Port=18090)
$ErrorActionPreference="Stop"
if($ImageProject -notmatch '^[a-z0-9-]+$'){throw "Invalid image project"}
if($Port -lt 1024 -or $Port -gt 65535){throw "Invalid local test port"}
$testProject="erudit-platform-check-"+[guid]::NewGuid().ToString("N").Substring(0,8)
$repoRoot=(Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$null=New-Item -ItemType Directory -Path (Join-Path $repoRoot "build") -Force
$override=Join-Path $repoRoot "build/$testProject.yml"
$apps=@("config-server","discovery-server","gateway","user-service","content-service","quiz-service","event-receiver","ai-platform","notificator","payment-service","admin-service")
$lines=@("services:")+@($apps|ForEach-Object{"  ${_}:`n    image: ${ImageProject}-${_}:latest"})
[IO.File]::WriteAllLines($override,$lines)
$composeArgs=@("compose","--project-directory",$repoRoot,"-p",$testProject,"-f",(Join-Path $repoRoot "docker-compose.yml"),"-f",$override)
$oldPort=$env:GATEWAY_PORT;$env:GATEWAY_PORT="$Port"
function Invoke-PlatformCompose([string[]]$Arguments){& docker @composeArgs @Arguments;if($LASTEXITCODE -ne 0){throw "Platform command failed: $Arguments"}}
try{
    # Reuse the verified image binaries, with normal profiles and entirely fresh databases/networks.
    Invoke-PlatformCompose @("config","--quiet")
    Invoke-PlatformCompose @("up","-d","--no-build","--wait","--wait-timeout","300")
    $ids=@(& docker ps --filter "label=com.docker.compose.project=$testProject" --format '{{.ID}}')
    if($LASTEXITCODE -ne 0 -or $ids.Count -ne 19){throw "Expected 11 applications and 8 stores"}
    foreach($id in $ids){
        $service=& docker inspect $id --format '{{index .Config.Labels "com.docker.compose.service"}}'
        $health=& docker inspect $id --format '{{.State.Health.Status}}'
        if($LASTEXITCODE -ne 0 -or $health -ne 'healthy'){throw "$service is unhealthy"}
        $bindings=& docker inspect $id --format '{{json .HostConfig.PortBindings}}'
        if($bindings -notin @('{}','null') -and $service -ne 'gateway'){throw "Unexpected public ports: $service"}
    }
    Invoke-PlatformCompose @("restart","user-service")
    Invoke-PlatformCompose @("up","-d","--no-build","--wait","--wait-timeout","180","user-service")
    $ready=$false
    for($attempt=0;$attempt -lt 60;$attempt++){
        try{$users=Invoke-RestMethod "http://localhost:$Port/api/v1/users" -TimeoutSec 5;if($users.Count -ne 1){throw "Demo fixtures leaked into normal profile"};$ready=$true;break}catch{Start-Sleep -Seconds 2}
    }
    if(-not $ready){throw "Normal User API did not recover after restart"}
    Write-Output "Platform smoke passed: 19 healthy containers, Gateway-only public ports, normal fixtures, User restart without rebuild."
}catch{& docker @composeArgs logs --tail 60;throw}finally{
    # Only this generated test project's containers/volumes and its own temporary override.
    & docker @composeArgs down --volumes --remove-orphans
    Remove-Item -LiteralPath $override -Force
    $env:GATEWAY_PORT=$oldPort
}
