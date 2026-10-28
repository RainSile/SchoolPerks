$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$directory=Join-Path $root '.local/business-tests/package'
New-Item -ItemType Directory -Force -Path $directory | Out-Null
$settings=@{
 SPRING_PROFILES_ACTIVE='skeleton';SERVER_ADDRESS='127.0.0.1';SERVER_PORT='0';API_DOCS_ENABLED='true'
 AUTH_ENABLED='false';JWT_ENABLED='false';AI_ENABLED='false';ASSISTANT_ENABLED='false';MARKETING_AI_ENABLED='false'
 REDIS_ENABLED='false';MONGO_ENABLED='false';RABBITMQ_ENABLED='false';PINECONE_ENABLED='false';AMAP_ENABLED='false'
 SMS_SIMULATED_ENABLED='false';SMS_CODE_LENGTH='6';SEARCH_BACKEND='MYSQL';ALIYUN_PNVS_ENABLED='false';CAMPUS_PNVS_LIVE_TEST='false'
 SEARCH_REBUILD_ENABLED='false';NOTIFICATION_DISPATCH_ENABLED='false';TRADE_SCHEDULER_ENABLED='false'
}
$application=$null
try {
 # A no-credentials smoke must not inherit private or empty provider settings from earlier tests.
 # Only the child environment changes; the caller's environment is untouched.
 $childEnvironment=@{}
 foreach($source in Get-ChildItem (Join-Path $root 'backend/campus-boot/src/main/resources') -Filter 'application*.yml'){
   foreach($match in [regex]::Matches((Get-Content -Raw $source.FullName),'\$\{([A-Z][A-Z0-9_]*)(?::[^}]*)?\}')){$childEnvironment[$match.Groups[1].Value]=$null}
 }
 foreach($name in @('SPRING_APPLICATION_JSON','SPRING_CONFIG_LOCATION','SPRING_CONFIG_ADDITIONAL_LOCATION')){$childEnvironment[$name]=$null}
 foreach($key in $settings.Keys){$childEnvironment[$key]=$settings[$key]}
 $java='E:\JDK17\bin\java.exe'
 $jar=Join-Path $root 'backend/campus-boot/target/campus-boot-0.1.0-SNAPSHOT.jar'
 $out=Join-Path $directory 'stdout.log'
 $application=Start-Process -FilePath $java -ArgumentList @('-jar',('"'+$jar+'"')) -WorkingDirectory $root -Environment $childEnvironment -WindowStyle Hidden -PassThru -RedirectStandardOutput $out -RedirectStandardError (Join-Path $directory 'stderr.log')
 $deadline=[DateTime]::UtcNow.AddSeconds(55)
 do {
   Start-Sleep -Milliseconds 250
   $application.Refresh()
   if($application.HasExited -or [DateTime]::UtcNow -gt $deadline){throw 'Package startup failed. Inspect private package logs.'}
   $hit=[regex]::Match((' '+(Get-Content -LiteralPath $out -Raw -ErrorAction SilentlyContinue)),'Tomcat started on port ([0-9]+)')
 } while(-not $hit.Success)
 $origin='http://127.0.0.1:'+$hit.Groups[1].Value
 if((Invoke-RestMethod "$origin/actuator/health" -TimeoutSec 10).status -ne 'UP'){throw 'Health check failed'}
 $docs=Invoke-RestMethod "$origin/v3/api-docs" -TimeoutSec 10
 $checked=0
 foreach($path in $docs.paths.PSObject.Properties){
   if($path.Name -match '/assistant/|/marketing'){continue}
   foreach($operation in $path.Value.PSObject.Properties){
     if($operation.Name -in @('get','post','put','patch','delete')){
       if(-not $operation.Value.summary -or -not $operation.Value.tags){throw "Missing OpenAPI description: $($path.Name)"}
       $checked++
     }
   }
 }
 foreach($path in @('/api/v1/users/me','/api/v1/orders','/api/v1/admin/roles','/api/v1/admin/dashboard','/api/v1/users/me/coupon-claims','/api/v1/users/me/merchants')){
   if((Invoke-WebRequest ($origin+$path) -SkipHttpErrorCheck -TimeoutSec 10).StatusCode -ne 401){throw "Anonymous access must be denied: $path"}
 }
 if((Invoke-WebRequest "$origin/doc.html" -TimeoutSec 10).StatusCode -ne 200){throw 'Knife4j unavailable'}
 [pscustomobject]@{passed=$true;businessOperations=$checked;anonymousResourcesChecked=6;health='UP';profile='skeleton';databaseConnected=$false;assistantRequested=$false} | ConvertTo-Json | Set-Content (Join-Path $directory 'result.json')
 Write-Output "PASS: packaged JAR, health, Knife4j, $checked documented business operations and six anonymous denials. Skeleton smoke only."
} finally {
 if($application){$application.Refresh();if(-not $application.HasExited){Stop-Process -Id $application.Id;Wait-Process -Id $application.Id -Timeout 15 -ErrorAction SilentlyContinue}}
}
