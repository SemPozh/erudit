param([string]$Project="erudit-demo",[int]$Port=18089,[switch]$SkipBuild)
$ErrorActionPreference="Stop"
if($Project -notmatch '^erudit-demo(?:-[a-z0-9]+)?$'){throw "Reset is restricted to erudit-demo or erudit-demo-<suffix>"}
if($Port -lt 1024 -or $Port -gt 65535){throw "Choose a local demo port between 1024 and 65535"}
$oldPort=$env:GATEWAY_PORT;$oldParallel=$env:COMPOSE_PARALLEL_LIMIT
$env:GATEWAY_PORT="$Port";$env:COMPOSE_PARALLEL_LIMIT="2"
$repoRoot=(Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$composeArgs=@("compose","--project-directory",$repoRoot,"--project-name",$Project,"-f",(Join-Path $repoRoot "docker-compose.yml"),"-f",(Join-Path $repoRoot "docker-compose.demo.yml"),"--profile","demo")
function Invoke-DemoCompose([string[]]$Arguments){
    & docker @composeArgs @Arguments
    if($LASTEXITCODE -ne 0){throw "Demo Compose command failed: $Arguments"}
}
try{
    # The project allowlist and project labels bound deletion to disposable local demo data.
    $volumes=& docker volume ls --filter "label=com.docker.compose.project=$Project" --format '{{.Name}}'
    if($LASTEXITCODE -ne 0){throw "Cannot inspect demo volumes"}
    foreach($volume in $volumes){
        $owner=& docker volume inspect $volume --format '{{index .Labels "com.docker.compose.project"}}'
        if($LASTEXITCODE -ne 0 -or $owner -ne $Project){throw "Refusing to reset a foreign volume"}
    }
    Invoke-DemoCompose @("down","--volumes","--remove-orphans")
    if(-not $SkipBuild){
        & (Join-Path $PSScriptRoot 'build-images.ps1') -Project $Project
        if($LASTEXITCODE -ne 0){throw 'Verified image build failed'}
    }
    Invoke-DemoCompose @("up","-d","--no-build","--wait","--wait-timeout","300")
    # Container health precedes Eureka registration and Gateway's discovery cache.
    $ready=$false
    for($attempt=0;$attempt -lt 90;$attempt++){
        try{
            foreach($path in @('/api/v1/users','/api/v1/admin/users','/api/v1/admin/content/moderation','/api/v1/quizzes/daily','/api/v1/subscriptions/plans','/api/v1/notifications/templates')){
                $null=Invoke-WebRequest "http://localhost:$Port$path" -TimeoutSec 5
            }
            $ready=$true;break
        }catch{Start-Sleep -Seconds 2}
    }
    if(-not $ready){throw 'Gateway discovery did not become ready'}
    Write-Output "Demo is healthy: http://localhost:$Port/swagger-ui/index.html (project $Project)"
}finally{$env:GATEWAY_PORT=$oldPort;$env:COMPOSE_PARALLEL_LIMIT=$oldParallel}
