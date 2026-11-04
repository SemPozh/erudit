param([string]$Project="erudit-demo",[string]$Revision="")
$ErrorActionPreference="Stop"
if($Project -notmatch '^[a-z0-9-]+$'){throw 'Invalid local image project'}
$repoRoot=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
if(-not $Revision){$Revision=& git -C $repoRoot rev-parse HEAD}
if($Revision -notmatch '^[a-f0-9]{40}$'){throw 'Build revision must be a full Git SHA'}
$oldRevision=$env:BUILD_REVISION;$oldDate=$env:BUILD_DATE;$oldState=$env:BUILD_WORKTREE_STATE
$env:BUILD_REVISION=$Revision;$env:BUILD_DATE=[DateTimeOffset]::UtcNow.ToString('o')
$dirty=& git -C $repoRoot status --porcelain --untracked-files=all
$env:BUILD_WORKTREE_STATE=if($dirty){'dirty'}else{'clean'}
$composeArgs=@('compose','--project-directory',$repoRoot,'-p',$Project,'-f',(Join-Path $repoRoot 'docker-compose.yml'))
$apps=@('config-server','discovery-server','gateway','user-service','content-service','quiz-service','event-receiver','ai-platform','notificator','payment-service','admin-service')
$report=@()
try{
    for($offset=0;$offset -lt $apps.Count;$offset+=2){
        & docker @composeArgs build @($apps|Select-Object -Skip $offset -First 2)
        if($LASTEXITCODE -ne 0){throw 'Image build failed'}
    }
    foreach($app in $apps){
        $tag="${Project}-${app}:latest"
        $info=(& docker image inspect $tag|ConvertFrom-Json)[0]
        if($LASTEXITCODE -ne 0){throw "Missing image $tag"}
        if($info.Config.Labels.'org.opencontainers.image.revision' -ne $Revision){throw "Incorrect revision: $tag"}
        if($info.Config.Labels.'org.opencontainers.image.created' -ne $env:BUILD_DATE){throw "Incorrect build timestamp: $tag"}
        $uid=& docker run --rm --network none --entrypoint id $tag -u
        if($LASTEXITCODE -ne 0 -or $uid -ne '10001'){throw "Runtime must be non-root UID 10001: $tag"}
        $report+=@{module=$app;image=$tag;digest=$info.Id;revision=$Revision;created=$env:BUILD_DATE;uid=$uid;state=$env:BUILD_WORKTREE_STATE}
    }
    $null=New-Item -ItemType Directory (Join-Path $repoRoot 'build') -Force
    $report|ConvertTo-Json -Depth 5|Set-Content (Join-Path $repoRoot 'build/images-report.json')
    Write-Output 'Image checks passed: all 11 applications, immutable content digests, Git/UTC labels and non-root UID 10001.'
}finally{$env:BUILD_REVISION=$oldRevision;$env:BUILD_DATE=$oldDate;$env:BUILD_WORKTREE_STATE=$oldState}
