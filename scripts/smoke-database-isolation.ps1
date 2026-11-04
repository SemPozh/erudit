param([string[]]$Domains=@("user","content","quiz","event","notification","payment","admin"))
$ErrorActionPreference="Stop"
$owners=@{
    user=@("user-service","user-postgres");content=@("content-service","content-postgres")
    quiz=@("quiz-service","quiz-postgres");event=@("event-receiver","event-postgres","event-clickhouse")
    notification=@("notificator","notification-postgres");payment=@("payment-service","payment-postgres")
    admin=@("admin-service","admin-postgres")
}
foreach($domain in $Domains){if(-not $owners.ContainsKey($domain)){throw "Unknown domain: $domain"}}
$testProject="erudit-isolation-"+[guid]::NewGuid().ToString("N").Substring(0,8)
$composeArgs=@("compose","--project-name",$testProject,"--file","docker-compose.yml")
$oldParallel=$env:COMPOSE_PARALLEL_LIMIT
$env:COMPOSE_PARALLEL_LIMIT="2"
function Invoke-Compose([string[]]$Arguments){
    & docker @composeArgs @Arguments
    if($LASTEXITCODE -ne 0){throw "Compose command failed: $Arguments"}
}
try{
    $services=@($Domains|ForEach-Object{$owners[$_][0]})
    $buildServices=@("config-server","discovery-server")+$services
    # Bound the number of simultaneous Gradle daemons even when Compose uses Bake.
    for($offset=0;$offset -lt $buildServices.Count;$offset+=2){
        Invoke-Compose (@("build")+@($buildServices|Select-Object -Skip $offset -First 2))
    }
    foreach($domain in $Domains){
        $service=$owners[$domain][0];$database=$owners[$domain][1]
        Invoke-Compose @("up","-d","--no-build","--wait","--wait-timeout","180",$service)
        $running=& docker @composeArgs ps --services --status running
        if($LASTEXITCODE -ne 0){throw "Cannot list isolation containers"}
        foreach($other in $running){
            if($other -like "*-postgres" -and $other -ne $database){throw "Foreign database is running: $other"}
        }
        $flags=& docker @composeArgs exec -T $database sh -c 'PGPASSWORD="$APP_DB_PASSWORD" psql -h 127.0.0.1 -U "$APP_DB_USER" -d "$POSTGRES_DB" -tAc "SELECT rolsuper,rolcreatedb,rolcreaterole,rolreplication FROM pg_roles WHERE rolname=current_user"'
        if($LASTEXITCODE -ne 0 -or ($flags -join "").Trim() -ne "f|f|f|f"){throw "$domain application role has excessive rights"}
        $history=& docker @composeArgs exec -T $database sh -c 'PGPASSWORD="$APP_DB_PASSWORD" psql -h 127.0.0.1 -U "$APP_DB_USER" -d "$POSTGRES_DB" -tAc "SELECT count(*) FROM flyway_schema_history WHERE success"'
        if($LASTEXITCODE -ne 0 -or [int](($history -join "").Trim()) -lt 1){throw "$domain has no successful Flyway history"}
        Write-Output "Isolation passed: $domain healthy, own Flyway history, non-superuser; foreign business databases stopped."
        Invoke-Compose (@("stop")+$owners[$domain])
    }
}catch{
    if($service){& docker @composeArgs logs --tail 80 $service}
    throw
}finally{
    # Only this script's freshly generated project and disposable test volumes.
    & docker @composeArgs down --volumes --remove-orphans
    $env:COMPOSE_PARALLEL_LIMIT=$oldParallel
}
