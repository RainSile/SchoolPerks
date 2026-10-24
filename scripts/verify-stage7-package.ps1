$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$logs=Join-Path $root '.local'
New-Item -ItemType Directory -Force -Path $logs | Out-Null
$settings=@{
    SPRING_PROFILES_ACTIVE='skeleton';SERVER_ADDRESS='127.0.0.1';SERVER_PORT='0';API_DOCS_ENABLED='true'
    AUTH_ENABLED='false';JWT_ENABLED='false';AI_ENABLED='false';ASSISTANT_ENABLED='false';MARKETING_AI_ENABLED='false'
    REDIS_ENABLED='false';MONGO_ENABLED='false';RABBITMQ_ENABLED='false';PINECONE_ENABLED='false';AMAP_ENABLED='false'
    SMS_SIMULATED_ENABLED='false';SMS_CODE_LENGTH='6';SEARCH_BACKEND='MYSQL'
    SEARCH_REBUILD_ENABLED='false';NOTIFICATION_DISPATCH_ENABLED='false';TRADE_SCHEDULER_ENABLED='false'
}
$saved=@{};$application=$null
try {
    foreach($key in $settings.Keys){$saved[$key]=[Environment]::GetEnvironmentVariable($key,'Process');[Environment]::SetEnvironmentVariable($key,$settings[$key],'Process')}
    $jar=Join-Path $root 'backend/campus-boot/target/campus-boot-0.1.0-SNAPSHOT.jar'
    $out=Join-Path $logs 'stage7-package.out.log'
    $application=Start-Process -FilePath (Join-Path $env:JAVA_HOME 'bin/java.exe') -ArgumentList @('-jar',('"'+$jar+'"')) -WorkingDirectory $root -WindowStyle Hidden -PassThru -RedirectStandardOutput $out -RedirectStandardError (Join-Path $logs 'stage7-package.err.log')
    $deadline=[DateTime]::UtcNow.AddSeconds(55)
    do {
        Start-Sleep -Milliseconds 250
        $application.Refresh()
        if($application.HasExited -or [DateTime]::UtcNow -gt $deadline){throw 'Package startup failed; inspect local stage7-package logs.'}
        $portHit=[regex]::Match((' '+(Get-Content -LiteralPath $out -Raw -ErrorAction SilentlyContinue)),'Tomcat started on port ([0-9]+)')
    } while(-not $portHit.Success)
    $origin='http://127.0.0.1:'+$portHit.Groups[1].Value
    if((Invoke-RestMethod "$origin/actuator/health" -TimeoutSec 10).status -ne 'UP'){throw 'Health check failed'}
    $docs=Invoke-RestMethod "$origin/v3/api-docs" -TimeoutSec 10
    foreach($path in @('/api/v1/merchants/{merchantId}/dashboard','/api/v1/admin/dashboard','/api/v1/admin/operation-audit-events','/api/v1/merchant/dashboard')){
        if(-not $docs.paths.PSObject.Properties[$path]){throw "Missing stage seven resource $path"}
        if(-not $docs.paths.PSObject.Properties[$path].Value.get.security){throw "Missing Bearer declaration $path"}
    }
    foreach($path in $docs.paths.PSObject.Properties){foreach($operation in $path.Value.PSObject.Properties){
        if($operation.Name -in @('get','post','put','patch','delete') -and (-not $operation.Value.summary -or -not $operation.Value.tags)){throw "Missing OpenAPI description: $($path.Name)"}
    }}
    foreach($path in @('/api/v1/merchants/1/dashboard','/api/v1/admin/dashboard','/api/v1/admin/operation-audit-events')){
        if((Invoke-WebRequest ($origin+$path) -SkipHttpErrorCheck -TimeoutSec 10).StatusCode -ne 401){throw "Anonymous access must be denied $path"}
    }
    if((Invoke-WebRequest "$origin/doc.html" -TimeoutSec 10).StatusCode -ne 200){throw 'Knife4j unavailable'}
    if((Invoke-RestMethod "$origin/api/v1/assistant/capabilities" -TimeoutSec 10).enabled){throw 'Unconfigured AI must remain unavailable'}
    Write-Output ('PASS: packaged JAR without credentials, stage seven resources and Bearer schemas, anonymous 401, Knife4j and '+@($docs.paths.PSObject.Properties).Count+' documented API paths.')
} finally {
    if($application){$application.Refresh();if(-not $application.HasExited){Stop-Process -Id $application.Id;Wait-Process -Id $application.Id -Timeout 15 -ErrorAction SilentlyContinue}}
    foreach($key in $saved.Keys){[Environment]::SetEnvironmentVariable($key,$saved[$key],'Process')}
}
