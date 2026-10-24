$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$logs = Join-Path $projectRoot '.local'
New-Item -ItemType Directory -Force -Path $logs | Out-Null
$jarPath = Join-Path $projectRoot 'backend/campus-boot/target/campus-boot-0.1.0-SNAPSHOT.jar'
$application = $null
$savedEnvironment = @{}
$settings = @{
    SPRING_PROFILES_ACTIVE = 'local'
    AUTH_ENABLED = 'true'
    JWT_ENABLED = 'true'
    ALIPAY_SANDBOX_ENABLED = 'false'
    NOTIFICATION_DISPATCH_ENABLED = 'false'
    TRADE_SCHEDULER_ENABLED = 'false'
    SERVER_ADDRESS = '127.0.0.1'
    SERVER_PORT = '0'
    MYSQL_PWD = $env:DB_PASSWORD
}
foreach ($name in @('JWT_SECRET_BASE64', 'OTP_HASH_SECRET_BASE64', 'IDENTITY_KEY_BASE64')) {
    $bytes = [byte[]]::new(32)
    [System.Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
    $settings[$name] = [Convert]::ToBase64String($bytes)
}
try {
    foreach ($name in $settings.Keys) {
        $savedEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
        [Environment]::SetEnvironmentVariable($name, $settings[$name], 'Process')
    }
    $outputLog = Join-Path $logs 'stage4-package.out.log'
    $errorLog = Join-Path $logs 'stage4-package.err.log'
    $application = Start-Process -FilePath (Join-Path $env:JAVA_HOME 'bin/java.exe') -ArgumentList @('-jar', ('"' + $jarPath + '"')) -WindowStyle Hidden -PassThru -RedirectStandardOutput $outputLog -RedirectStandardError $errorLog
    $deadline = [DateTime]::UtcNow.AddSeconds(55)
    do {
        Start-Sleep -Milliseconds 250
        $output = Get-Content -LiteralPath $outputLog -Raw -ErrorAction SilentlyContinue
        $match = [regex]::Match((' ' + $output), 'Tomcat started on port (\d+)')
        if ($application.HasExited -or [DateTime]::UtcNow -gt $deadline) { throw 'Package did not start; inspect stage4-package logs.' }
    } while (-not $match.Success)
    $baseUri = 'http://127.0.0.1:' + $match.Groups[1].Value
    $catalog = Invoke-RestMethod -Uri ($baseUri + '/api/v1/trade-offers') -TimeoutSec 10
    if ($null -eq $catalog.items -or $catalog.page -ne 1) { throw 'Trade catalog is not a page.' }
    $capabilities = Invoke-RestMethod -Uri ($baseUri + '/api/v1/trade-capabilities') -TimeoutSec 10
    if (-not $capabilities.enabled -or $capabilities.paymentAvailable -or $capabilities.paymentEnvironment -ne 'ALIPAY_SANDBOX') { throw 'Sandbox capability or test-provider isolation failed.' }
    $storage = Invoke-RestMethod -Uri ($baseUri + '/api/v1/storage-capabilities') -TimeoutSec 10
    $sms = Invoke-RestMethod -Uri ($baseUri + '/api/v1/authentication-capabilities') -TimeoutSec 10
    if ($storage.available -or $sms.smsAvailable) { throw 'A test provider leaked into the packaged application.' }
    foreach ($route in @('/api/v1/orders', '/api/v1/orders/1', '/api/v1/merchants/1/orders')) {
        $response = Invoke-WebRequest -Uri ($baseUri + $route) -SkipHttpErrorCheck -TimeoutSec 10
        if ($response.StatusCode -ne 401) { throw "Anonymous access was not rejected for $route" }
    }
    $callback = Invoke-WebRequest -Uri ($baseUri + '/api/v1/payment-notifications/alipay-sandbox') -Method Post -ContentType 'application/x-www-form-urlencoded' -Body 'notify_id=invalid' -SkipHttpErrorCheck -TimeoutSec 10
    if ($callback.StatusCode -ne 503) { throw 'Unconfigured payment callback must fail explicitly.' }
    $docs = Invoke-RestMethod -Uri ($baseUri + '/v3/api-docs') -TimeoutSec 10
    foreach ($path in $docs.paths.PSObject.Properties) {
        foreach ($method in $path.Value.PSObject.Properties) {
            if ($method.Name -in @('get','post','put','patch','delete') -and (-not $method.Value.summary -or -not $method.Value.tags)) { throw "Missing OpenAPI summary/tags: $($path.Name)" }
        }
    }
    if ((Invoke-WebRequest -Uri ($baseUri + '/doc.html') -TimeoutSec 10).StatusCode -ne 200) { throw 'Knife4j was not served.' }
    Write-Output ('PASS: packaged JAR; real trade catalog; private routes 401; unconfigured callback 503; sandbox/SMS/storage mocks absent; Knife4j and ' + @($docs.paths.PSObject.Properties).Count + ' documented paths.')
    $mysql = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe'
    $sqlArgs = @('--host=127.0.0.1','--port=3306',('--user=' + $env:DB_USERNAME),'--batch','--skip-column-names','campus_perks_db')
    $tables = @(& $mysql @sqlArgs -e "SELECT table_name FROM information_schema.tables WHERE table_schema='campus_perks_db' AND table_type='BASE TABLE' AND table_name<>'flyway_schema_history';")
    if ($LASTEXITCODE -ne 0) { throw 'Scoped database read failed.' }
    $queries = @()
    foreach ($table in $tables) {
        if ($table -notmatch '^campus_[a-z_]+$') { throw 'Unexpected table name.' }
        $queries += "SELECT '$table',COUNT(*) FROM $table"
    }
    $counts = @(& $mysql @sqlArgs -e (($queries -join ';') + ';'))
    if ($LASTEXITCODE -ne 0) { throw 'Scoped table counts failed.' }
    $nonempty = @($counts | Where-Object { ($_ -split "`t")[-1] -ne '0' })
    $migrations = & $mysql @sqlArgs -e 'SELECT GROUP_CONCAT(version ORDER BY installed_rank) FROM flyway_schema_history WHERE success=1;'
    Write-Output ('Database: ' + $tables.Count + ' business tables; migrations=' + $migrations + '; nonempty tables=' + $nonempty.Count)
    if ($nonempty.Count) { Write-Output $nonempty }
} finally {
    if ($application -and -not $application.HasExited) { Stop-Process -Id $application.Id; $application.WaitForExit() }
    foreach ($name in $savedEnvironment.Keys) { [Environment]::SetEnvironmentVariable($name, $savedEnvironment[$name], 'Process') }
}
