param([string]$BaseUrl="http://localhost:8089")
$ErrorActionPreference="Stop"
$ready=$false
for($attempt=0;$attempt -lt 60;$attempt++){
    try{$config=Invoke-RestMethod "$BaseUrl/openapi/swagger-config" -TimeoutSec 5;$ready=$true;break}catch{Start-Sleep -Seconds 2}
}
if(-not $ready){throw "Swagger catalog is unavailable"}
if($config.urls.Count -ne 8){throw "Expected every business module"}
foreach($entry in $config.urls){
    $raw=Invoke-WebRequest "$BaseUrl$($entry.url)" -TimeoutSec 10
    if($raw.Content -match '/internal/|securitySchemes'){throw "Private contract exposed: $($entry.name)"}
    $spec=$raw.Content|ConvertFrom-Json
    if($spec.servers[0].url -ne '/'){throw "Try it out must use Gateway origin"}
}
$ui=Invoke-WebRequest "$BaseUrl/swagger-ui/index.html" -TimeoutSec 10
if($ui.Content -notmatch 'swagger-ui'){throw "Swagger UI is unavailable"}
$initializer=Invoke-WebRequest "$BaseUrl/swagger-ui/swagger-initializer.js" -TimeoutSec 10
if($initializer.Content -notmatch '/openapi/swagger-config'){throw "Swagger UI is not connected to catalog"}
Write-Output "Swagger smoke passed: 8 modules, public-only contracts, Gateway origin, UI and catalog."
