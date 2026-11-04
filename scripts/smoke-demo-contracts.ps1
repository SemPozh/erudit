param([string]$BaseUrl="http://localhost:18089",[string]$Report="build/demo-contract-results.xml",[switch]$LibraryOnly,[switch]$TraceHttp)
$ErrorActionPreference="Stop"
$cases=[ordered]@{};$activeCase="readiness";$documents=@()
$headers=@{"X-User-Id"="00000000-0000-0000-0000-000000000001";"X-Request-ID"="demo-contract-"+[guid]::NewGuid()}
function Assert-Contract($condition,[string]$message){if(-not $condition){throw $message}}
function Resolve-Node($doc,$node){
    while($node -and $node.Contains('$ref')){
        $ref=$node['$ref'];Assert-Contract ($ref.StartsWith('#/')) "External reference is unsupported: $ref"
        $node=$doc;foreach($part in $ref.Substring(2).Split('/')){$node=$node[$part]}
    }
    return $node
}
function Assert-Schema($doc,$schema,$value,[string]$location='$'){
    $schema=Resolve-Node $doc $schema
    if($null -eq $value){Assert-Contract ($schema.nullable -eq $true) "Null not allowed at $location";return}
    foreach($parent in $schema.allOf){Assert-Schema $doc $parent $value $location}
    if($schema.enum){Assert-Contract ($schema.enum -ccontains $value) "Unexpected enum at $location"}
    switch($schema.type){
        object {
            Assert-Contract ($value -is [System.Collections.IDictionary]) "Expected object at $location"
            foreach($key in $schema.required){Assert-Contract ($value.Contains($key)) "Required field $location.$key missing"}
            foreach($key in $value.Keys){if($schema.properties -and $schema.properties.Contains($key)){Assert-Schema $doc $schema.properties[$key] $value[$key] "$location.$key"}}
        }
        array {
            Assert-Contract ($value -is [System.Collections.IList]) "Expected array at $location"
            if($schema.Contains('minItems')){Assert-Contract ($value.Count -ge $schema.minItems) "Too few items at $location"}
            if($schema.Contains('maxItems')){Assert-Contract ($value.Count -le $schema.maxItems) "Too many items at $location"}
            foreach($item in $value){Assert-Schema $doc $schema.items $item "$location[]"}
        }
        string {
            Assert-Contract ($value -is [string]) "Expected string at $location"
            if($schema.Contains('minLength')){Assert-Contract ($value.Length -ge $schema.minLength) "String too short at $location"}
            if($schema.Contains('maxLength')){Assert-Contract ($value.Length -le $schema.maxLength) "String too long at $location"}
            if($schema.format -eq 'uuid'){$parsed=[guid]::Empty;Assert-Contract ([guid]::TryParse($value,[ref]$parsed)) "Invalid UUID at $location"}
            if($schema.format -eq 'date-time'){$parsed=[DateTimeOffset]::MinValue;Assert-Contract ([DateTimeOffset]::TryParse($value,[ref]$parsed)) "Invalid timestamp at $location"}
        }
        boolean {Assert-Contract ($value -is [bool]) "Expected boolean at $location"}
        integer {Assert-Contract ($value -is [System.ValueType] -and $value -isnot [bool] -and [math]::Floor([double]$value) -eq [double]$value) "Expected integer at $location"}
        number {Assert-Contract ($value -is [System.ValueType] -and $value -isnot [bool]) "Expected number at $location"}
    }
}
function Invoke-Contract([string]$method,[string]$path,[int]$expected=200,$body=$null){
    $script:activeCase="$method $path [$expected]";$cases[$script:activeCase]=$null
    $arguments=@{Uri="$BaseUrl$path";Method=$method;Headers=$headers;SkipHttpErrorCheck=$true;TimeoutSec=20}
    if($null -ne $body){$arguments.ContentType="application/json";$arguments.Body=($body|ConvertTo-Json -Depth 20 -Compress)}
    if($TraceHttp){Write-Host ("REQUEST $method $path headers="+($headers|ConvertTo-Json -Compress)+" body="+($body|ConvertTo-Json -Depth 20 -Compress))}
    $response=Invoke-WebRequest @arguments
    if($TraceHttp){Write-Host ("RESPONSE status="+$response.StatusCode+" headers="+($response.Headers|ConvertTo-Json -Compress)+" body="+$response.Content)}
    Assert-Contract ($response.StatusCode -eq $expected) "$method $path expected $expected, got $($response.StatusCode): $($response.Content)"
    Assert-Contract ([bool]$response.Headers['X-Request-ID']) "Missing request ID at $path"
    $value=if($response.Content){ConvertFrom-Json -InputObject $response.Content -AsHashtable -NoEnumerate -DateKind String}else{$null}
    if($expected -lt 300){
        $rawPath=($path -split '\?')[0];$found=$false
        foreach($doc in $documents){
            $template=if($doc.paths.Contains($rawPath)){$rawPath}else{
                @($doc.paths.Keys|Where-Object {
                    $pattern='^'+(($_.Split('/')|ForEach-Object{if($_.StartsWith('{')){'[^/]+'}else{[regex]::Escape($_)}})-join '/')+'$'
                    $rawPath -match $pattern
                })|Select-Object -First 1
            }
            if(-not $template -or -not $doc.paths[$template].Contains($method.ToLowerInvariant())){continue}
            $operation=$doc.paths[$template][$method.ToLowerInvariant()]
            $responseSchema=Resolve-Node $doc $operation.responses["$expected"]
            Assert-Contract ([bool]$responseSchema) "Undocumented success status for $path"
            if($responseSchema.content.'application/json'.schema){Assert-Schema $doc $responseSchema.content.'application/json'.schema $value}
            foreach($header in $responseSchema.headers.Keys){
                if($header -eq 'X-Next-Page' -and ($response.Headers['X-Has-More'] -join '') -eq 'false'){continue}
                Assert-Contract ([bool]$response.Headers[$header]) "Missing contract header $header at $path"
            }
            $found=$true;break
        }
        Assert-Contract $found "No public OpenAPI operation for $method $path"
    }
    Write-Host "[PASS] $method $path ($expected)"
    return $value
}
if($LibraryOnly){return}
try{
    $ready=$false
    for($attempt=0;$attempt -lt 90;$attempt++){
        try{
            foreach($path in @('/api/v1/users','/api/v1/admin/users','/api/v1/admin/content/moderation','/api/v1/quizzes/daily','/api/v1/subscriptions/plans','/api/v1/notifications/templates')){
                $null=Invoke-WebRequest "$BaseUrl$path" -Headers $headers -TimeoutSec 5
            }
            $ready=$true;break
        }catch{Start-Sleep -Seconds 2}
    }
    Assert-Contract $ready "Discovery/domain APIs did not become ready"
    $catalog=Invoke-RestMethod "$BaseUrl/openapi/swagger-config"
    foreach($url in $catalog.urls){$documents+=(Invoke-WebRequest "$BaseUrl$($url.url)").Content|ConvertFrom-Json -AsHashtable -DateKind String}
    $user='00000000-0000-0000-0000-000000000001';$article='11000000-0000-0000-0000-000000000001'
    foreach($path in @("/api/v1/users/$user",'/api/v1/friends','/api/v1/content?size=50',"/api/v1/content/$article","/api/v1/content/$article/quiz-cards",'/api/v1/content/feed','/api/v1/quizzes','/api/v1/quizzes/daily','/api/v1/assessments/questions','/api/v1/assessments/sessions/51000000-0000-0000-0000-000000000001/result','/api/v1/learning-program','/api/v1/competitions/30000000-0000-0000-0000-000000000001/leaderboard','/api/v1/subscriptions/plans','/api/v1/subscriptions/me','/api/v1/subscriptions/payments','/api/v1/notifications','/api/v1/notifications/templates','/api/v1/notifications/41000000-0000-0000-0000-000000000001/delivery-attempts','/api/v1/admin/users','/api/v1/admin/audit','/api/v1/admin/content/moderation','/api/v1/admin/content/reports')){$null=Invoke-Contract GET $path}
    $demoAttempts=@(Invoke-Contract GET '/api/v1/notifications/41000000-0000-0000-0000-000000000001/delivery-attempts')
    Assert-Contract (@($demoAttempts|Where-Object{$_.id -eq '42000000-0000-0000-0000-000000000001' -and $_.status -eq 'MOCK_DELIVERED'}).Count -eq 1) "Demo receipt must already be terminal, without scheduler resends"
    foreach($metric in @('active-users','sessions','engagement','learning','funnels','monetization','notifications')){
        $null=Invoke-Contract GET "/api/v1/analytics/$metric`?from=2026-01-01T00:00:00Z&to=2026-01-02T00:00:00Z"
    }
    foreach($path in @('/api/v1/users?size=51','/api/v1/content?size=51','/api/v1/subscriptions/payments?size=51','/api/v1/notifications?size=51','/api/v1/admin/audit?size=51','/api/v1/content?sort=unknown')){$null=Invoke-Contract GET $path 400}
    $activeCase='POST /api/v1/media multipart';$cases[$activeCase]=$null
    $client=[Net.Http.HttpClient]::new();$multipart=[Net.Http.MultipartFormDataContent]::new()
    try{
        $bytes=[Convert]::FromBase64String('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a+p0AAAAASUVORK5CYII=')
        $file=[Net.Http.ByteArrayContent]::new($bytes);$file.Headers.ContentType=[Net.Http.Headers.MediaTypeHeaderValue]::new('image/png')
        $multipart.Add($file,'file','demo.png')
        $upload=$client.PostAsync("$BaseUrl/api/v1/media",$multipart).GetAwaiter().GetResult()
        Assert-Contract ([int]$upload.StatusCode -eq 201) "Fresh media volume is not writable: $($upload.StatusCode)"
        $media=$upload.Content.ReadAsStringAsync().GetAwaiter().GetResult()|ConvertFrom-Json -AsHashtable -DateKind String
        $mediaDoc=$documents|Where-Object{$_.paths.Contains('/api/v1/media')}|Select-Object -First 1
        Assert-Schema $mediaDoc $mediaDoc.paths['/api/v1/media'].post.responses['201'].content.'application/json'.schema $media
        $activeCase="GET $($media.url) binary";$cases[$activeCase]=$null
        $download=$client.GetAsync("$BaseUrl$($media.url)").GetAwaiter().GetResult()
        $downloaded=$download.Content.ReadAsByteArrayAsync().GetAwaiter().GetResult()
        Assert-Contract ([int]$download.StatusCode -eq 200 -and [Convert]::ToBase64String($downloaded) -eq [Convert]::ToBase64String($bytes)) "Media bytes did not round-trip"
        Assert-Contract ($mediaDoc.paths['/api/v1/media/{id}'].get.responses['200'].content.Contains($download.Content.Headers.ContentType.MediaType)) "Undocumented binary media type"
        Write-Host '[PASS] Media multipart upload/download and binary contract'
    }finally{$multipart.Dispose();$client.Dispose()}
    $category=Invoke-Contract POST '/api/v1/content/categories' 201 @{name="Contract-$([guid]::NewGuid())"}
    $id=$category.data.id
    $null=Invoke-Contract PUT "/api/v1/content/categories/$id" 200 @{name="Updated-$([guid]::NewGuid())"}
    $null=Invoke-Contract DELETE "/api/v1/content/categories/$id" 204
    $null=Invoke-Contract POST "/api/v1/content/$article/view" 200
    $null=Invoke-Contract POST "/api/v1/content/$article/complete" 200
    $null=Invoke-Contract POST '/api/v1/quizzes/20000000-0000-0000-0000-000000000001/attempts' 201
    $null=Invoke-Contract POST '/api/v1/quiz-attempts/52000000-0000-0000-0000-000000000001/hint' 200
    $null=Invoke-Contract POST '/api/v1/quiz-attempts/52000000-0000-0000-0000-000000000001/submit' 200 @{optionIndex=1}
    $null=Invoke-Contract POST '/api/v1/quiz-attempts/52000000-0000-0000-0000-000000000001/submit' 409 @{optionIndex=1}
    $null=Invoke-Contract POST '/api/v1/notifications/41000000-0000-0000-0000-000000000001/read' 204
    $null=Invoke-Contract POST '/api/v1/admin/content/11000000-0000-0000-0000-000000000002/approve' 200
    $null=Invoke-Contract POST '/api/v1/admin/content/reports/12000000-0000-0000-0000-000000000001/resolve' 204 @{decision='REJECT';comment='Contract reviewed'}
    $event=@{eventId='e0000000-0000-0000-0000-000000000099';userId=$user;eventType='SESSION_STARTED';occurredAt='2026-01-01T12:00:00Z';payload=@{source='contract'}}
    $first=Invoke-Contract POST '/api/v1/events' 202 $event
    $repeat=Invoke-Contract POST '/api/v1/events' 202 $event
    Assert-Contract (-not $first.duplicate -and $repeat.duplicate) "Event idempotency failed"
    $analytics=Invoke-Contract GET '/api/v1/analytics/active-users?from=2026-01-01T00:00:00Z&to=2026-01-02T00:00:00Z'
    Assert-Contract ($analytics.count -ge 5) "Demo analytics fixtures missing"
    Write-Output "Demo HTTP contract suite passed: $($cases.Count) operations/status checks, all domain stores, no SQL/reset endpoints."
}catch{$cases[$activeCase]=$_.Exception.Message;throw}finally{
    $directory=Split-Path $Report -Parent;if($directory){$null=New-Item -ItemType Directory -Path $directory -Force}
    $xml=[System.Xml.XmlDocument]::new();$suite=$xml.CreateElement('testsuite');$null=$xml.AppendChild($suite)
    $suite.SetAttribute('name','demo-http-contracts');$suite.SetAttribute('tests',"$($cases.Count)");$suite.SetAttribute('failures',"$(@($cases.Values|Where-Object{$null -ne $_}).Count)")
    foreach($entry in $cases.GetEnumerator()){
        $case=$xml.CreateElement('testcase');$case.SetAttribute('name',$entry.Key);$null=$suite.AppendChild($case)
        if($null -ne $entry.Value){$failure=$xml.CreateElement('failure');$failure.SetAttribute('message',$entry.Value);$null=$case.AppendChild($failure)}
    }
    $xml.Save((Join-Path (Get-Location) $Report))
}
