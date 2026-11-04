param([string]$BaseUrl="http://localhost:18089",[string]$Report="build/gateway-e2e.xml")
$ErrorActionPreference="Stop"
. (Join-Path $PSScriptRoot "smoke-demo-contracts.ps1") -BaseUrl $BaseUrl -Report $Report -LibraryOnly -TraceHttp
$catalog=Invoke-RestMethod "$BaseUrl/openapi/swagger-config"
foreach($url in $catalog.urls){$documents+=(Invoke-WebRequest "$BaseUrl$($url.url)").Content|ConvertFrom-Json -AsHashtable -DateKind String}
$userId=$null;$contentId=$null;$categoryId=$null
try{
    $user=Invoke-Contract POST '/api/v1/users/register' 201 @{email="e2e-$([guid]::NewGuid())@erudit.local";displayName='Gateway learner'}
    $userId=$user.id;$headers['X-User-Id']=$userId
    $null=Invoke-Contract POST '/api/v1/users/verify' 204 @{token='demo-verification-token'}
    $null=Invoke-Contract PUT "/api/v1/users/$userId" 200 @{displayName='Updated Gateway learner'}
    $profile=Invoke-Contract GET "/api/v1/users/$userId"
    Assert-Contract ($profile.verified -and $profile.displayName -eq 'Updated Gateway learner') 'User lifecycle did not persist'
    $category=Invoke-Contract POST '/api/v1/content/categories' 201 @{name="E2E-$([guid]::NewGuid())"};$categoryId=$category.data.id
    $draft=@{categoryId=$categoryId;type='ARTICLE';title='Gateway content';description='E2E local source';body='The Earth orbits the Sun.';difficulty='BEGINNER';estimatedMinutes=1;premiumLocked=$false;tags=@('e2e')}
    $created=Invoke-Contract POST '/api/v1/content' 201 $draft;$contentId=$created.data.id
    $null=Invoke-Contract POST "/api/v1/admin/content/$contentId/approve" 200
    $article=Invoke-Contract GET "/api/v1/content/$contentId"
    Assert-Contract ($article.data.status -eq 'PUBLISHED') 'Moderation did not publish content'
    $null=Invoke-Contract POST "/api/v1/content/$contentId/complete" 200
    $session=Invoke-Contract POST '/api/v1/assessments/sessions' 201
    $questions=@(Invoke-Contract GET '/api/v1/assessments/questions')
    $answers=@($questions|ForEach-Object{@{questionId=$_.id;optionIndex=1}})
    $null=Invoke-Contract POST "/api/v1/assessments/sessions/$($session.id)/submit" 200 @{answers=$answers}
    $program=Invoke-Contract GET '/api/v1/learning-program';Assert-Contract ($program.userId -eq $userId) 'Program belongs to wrong user'
    $quiz=Invoke-Contract POST '/api/v1/quizzes' 201 @{title='E2E quiz';prompt='The Earth orbits?';options=@('Sun','Moon');correctIndex=0;hint='A star'}
    $attempt=Invoke-Contract POST "/api/v1/quizzes/$($quiz.id)/attempts" 201
    $null=Invoke-Contract POST "/api/v1/quiz-attempts/$($attempt.id)/hint" 200
    $result=Invoke-Contract POST "/api/v1/quiz-attempts/$($attempt.id)/submit" 200 @{optionIndex=0}
    Assert-Contract ($result.status -eq 'COMPLETED') 'Quiz was not completed'
    $competition=Invoke-Contract POST '/api/v1/competitions' 201 @{title='Gateway competition'}
    $null=Invoke-Contract POST "/api/v1/competitions/$($competition.id)/invite" 202 @{userId='00000000-0000-0000-0000-000000000002'}
    $headers['X-User-Id']='00000000-0000-0000-0000-000000000002'
    $null=Invoke-Contract POST "/api/v1/competitions/$($competition.id)/join" 204
    $members=@(Invoke-Contract GET "/api/v1/competitions/$($competition.id)/leaderboard")
    Assert-Contract (@($members|Where-Object{$_.userId -eq $headers['X-User-Id']}).Count -eq 1) 'Competition member missing'
    $null=Invoke-Contract GET '/api/v1/ratings?period=ALL'
    $headers['X-User-Id']=$userId
    $subscription=Invoke-Contract POST '/api/v1/subscriptions' 201 @{plan='PREMIUM_MONTHLY';paymentMethodId='mock_card'}
    $premium=Invoke-Contract GET '/api/v1/content/11000000-0000-0000-0000-000000000003'
    Assert-Contract ($premium.data.body -ne 'Premium content') 'Premium entitlement did not reach Content'
    $payments=Invoke-Contract GET '/api/v1/subscriptions/payments';Assert-Contract ($payments.data.Count -ge 1) 'Mock charge missing'
    $null=Invoke-Contract POST '/api/v1/admin/notifications/send-many' 200 @{userIds=@($userId);templateId='40000000-0000-0000-0000-000000000001';parameters=@{}}
    $inbox=Invoke-Contract GET '/api/v1/notifications';Assert-Contract ($inbox.data.Count -ge 1) 'Mock inbox empty'
    $null=Invoke-Contract POST "/api/v1/notifications/$($inbox.data[0].id)/read" 204
    $event=@{eventId=[guid]::NewGuid().ToString();userId=$userId;eventType='SESSION_STARTED';occurredAt=[DateTimeOffset]::UtcNow.ToString('o');payload=@{source='gateway-e2e'}}
    $null=Invoke-Contract POST '/api/v1/events' 202 $event
    $from=[DateTimeOffset]::UtcNow.AddMinutes(-5).ToString('yyyy-MM-ddTHH:mm:ssZ');$to=[DateTimeOffset]::UtcNow.AddMinutes(5).ToString('yyyy-MM-ddTHH:mm:ssZ')
    for($i=0;$i -lt 30;$i++){$metric=Invoke-Contract GET "/api/v1/analytics/sessions?from=$from&to=$to";if($metric.count -ge 1){break};Start-Sleep -Milliseconds 200}
    Assert-Contract ($metric.count -ge 1) 'Analytics event did not reach ClickHouse'
    $null=Invoke-Contract GET '/api/v1/admin/audit'
    $null=Invoke-Contract POST "/api/v1/subscriptions/$($subscription.data.id)/cancel" 200
    $null=Invoke-Contract DELETE "/api/v1/content/$contentId" 204;$contentId=$null
    Write-Output "Gateway E2E passed: $($cases.Count) steps without Authorization or database access."
}catch{$cases[$activeCase]=$_.Exception.Message;throw}finally{
    $lines=@('# Gateway E2E results','','Reproduce: reset-demo.ps1; smoke-e2e.ps1; smoke-resilience.ps1','','| Step | Result |','| --- | --- |')
    foreach($entry in $cases.GetEnumerator()){$state=if($null -eq $entry.Value){'PASS'}else{'FAIL: '+$entry.Value};$lines+="| $($entry.Key) | $state |"}
    $null=New-Item -ItemType Directory -Force (Split-Path $Report -Parent)
    [IO.File]::WriteAllLines((Join-Path (Get-Location) ($Report+'.md')),$lines)
    $xml=[xml]'<testsuite name="gateway-e2e" />';$suite=$xml.DocumentElement;$suite.SetAttribute('tests',"$($cases.Count)");$suite.SetAttribute('failures',"$(@($cases.Values|Where-Object{$null -ne $_}).Count)")
    foreach($entry in $cases.GetEnumerator()){$case=$xml.CreateElement('testcase');$case.SetAttribute('name',$entry.Key);$null=$suite.AppendChild($case);if($null -ne $entry.Value){$failure=$xml.CreateElement('failure');$failure.SetAttribute('message',$entry.Value);$null=$case.AppendChild($failure)}}
    $xml.Save((Join-Path (Get-Location) $Report))
    # Only own temporary user/content; reports and attempts may retain domain-local UUID references.
    if($contentId){try{$null=Invoke-WebRequest "$BaseUrl/api/v1/content/$contentId" -Method Delete -Headers $headers}catch{Write-Warning 'Own content cleanup failed'}}
    if($categoryId){Write-Host "Category $categoryId remains referenced by archived content; demo reset removes it."}
    if($userId){try{$null=Invoke-WebRequest "$BaseUrl/api/v1/users/$userId" -Method Delete}catch{Write-Warning 'Own user cleanup failed'}}
}
