param(
    [string]$BaseUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"
$client = [System.Net.Http.HttpClient]::new()
$script:passed = 0
$script:failed = 0

function Invoke-Api {
    param(
        [string]$Name,
        [string]$Method,
        [string]$Path,
        [AllowNull()][object]$Body = $null,
        [hashtable]$Headers = @{},
        [int[]]$ExpectedStatus = @(200)
    )

    $request = [System.Net.Http.HttpRequestMessage]::new(
        [System.Net.Http.HttpMethod]::new($Method),
        "$BaseUrl$Path"
    )
    foreach ($header in $Headers.GetEnumerator()) {
        [void]$request.Headers.TryAddWithoutValidation($header.Key, [string]$header.Value)
    }
    if ($null -ne $Body) {
        $json = $Body | ConvertTo-Json -Depth 20 -Compress
        $request.Content = [System.Net.Http.StringContent]::new(
            $json,
            [System.Text.Encoding]::UTF8,
            "application/json"
        )
    }

    $response = $client.SendAsync($request).GetAwaiter().GetResult()
    $status = [int]$response.StatusCode
    $text = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
    $ok = $ExpectedStatus -contains $status
    if ($status -eq 401) {
        $ok = $false
        Write-Host "[401] ${Name}: $Method $Path returned 401 although Lab 1 has no authorization" -ForegroundColor Red
    } elseif ($ok) {
        Write-Host "[PASS] ${Name}: $Method $Path -> $status" -ForegroundColor Green
    } else {
        Write-Host "[FAIL] ${Name}: $Method $Path -> $status; expected $($ExpectedStatus -join ', ')" -ForegroundColor Red
    }

    if ($ok) { $script:passed++ } else { $script:failed++ }
    [pscustomobject]@{ Status = $status; Body = $text; Headers = $response.Headers; Success = $ok }
}

try {
    $swagger = Invoke-Api "Swagger UI" GET "/swagger-ui/index.html" -ExpectedStatus 200
    $openApi = Invoke-Api "OpenAPI document" GET "/v3/api-docs" -ExpectedStatus 200

    $email = "smoke-$([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds())@example.com"
    $registration = Invoke-Api "Register user" POST "/api/v1/auth/register" @{
        email = $email
        password = "SmokePass123!"
        name = "Smoke Test"
    } -ExpectedStatus 201
    $registered = $registration.Body | ConvertFrom-Json
    $userId = [string]$registered.data.id
    if ([string]::IsNullOrWhiteSpace($userId)) { throw "Registration response does not contain data.id" }
    $identity = @{ "X-User-Id" = $userId }

    [void](Invoke-Api "Current profile" GET "/api/v1/users/me" -Headers $identity -ExpectedStatus 200)
    [void](Invoke-Api "Update settings" PUT "/api/v1/users/me/settings" @{
        language = "ru"
        timeZone = "Europe/Moscow"
        favoriteCategories = @()
        dailyGoalMinutes = 20
        visibleInSearch = $true
        visibleInRating = $true
        analyticsConsent = $true
    } -Headers $identity -ExpectedStatus 200)

    $categoriesResponse = Invoke-Api "List categories" GET "/api/v1/content/categories?page=0&size=10" -ExpectedStatus 200
    $categories = $categoriesResponse.Body | ConvertFrom-Json
    $categoryId = [string]$categories.data[0].id
    if ([string]::IsNullOrWhiteSpace($categoryId)) { throw "No categories available for content scenario" }

    # Deliberately no Authorization and no X-User-Id: this is the regression check for the reported 401.
    $createdResponse = Invoke-Api "Create content without authorization" POST "/api/v1/content" @{
        categoryId = $categoryId
        type = "ARTICLE"
        title = "Smoke content $([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds())"
        description = "Created by the local smoke test"
        body = "Smoke test body"
        difficulty = "BEGINNER"
        estimatedMinutes = 5
        premiumLocked = $false
        tags = @("smoke")
    } -ExpectedStatus 201
    $created = $createdResponse.Body | ConvertFrom-Json
    $contentId = [string]$created.data.id
    if ([string]::IsNullOrWhiteSpace($contentId)) { throw "Create content response does not contain data.id" }

    [void](Invoke-Api "Get created content" GET "/api/v1/content/$contentId" -ExpectedStatus 200)
    [void](Invoke-Api "List content" GET "/api/v1/content?page=0&size=10" -ExpectedStatus 200)
    [void](Invoke-Api "List formats" GET "/api/v1/content/formats" -ExpectedStatus 200)
    [void](Invoke-Api "Subscription plans" GET "/api/v1/subscriptions/plans" -ExpectedStatus 200)
    [void](Invoke-Api "Ingest event" POST "/api/v1/events" @{
        eventId = [guid]::NewGuid().ToString()
        userId = $userId
        sessionId = "smoke-session"
        eventType = "CONTENT_VIEWED"
        timestamp = [DateTimeOffset]::UtcNow.ToString("o")
        payload = @{ contentId = $contentId }
    } -ExpectedStatus 202)
} finally {
    $client.Dispose()
}

Write-Host "Smoke result: $script:passed passed, $script:failed failed"
if ($script:failed -gt 0) { exit 1 }

