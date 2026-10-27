param([switch]$SkipSnapshot,[string]$Phase='',[string]$TestSelection='',[switch]$UseMapConfiguration,[switch]$UseLocalHttpProxy,[switch]$UseObjectStorageConfiguration)
$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
if(-not $env:DB_USERNAME -or -not $env:DB_PASSWORD){throw 'Supply authorized database credentials through the process environment.'}
if($env:DB_NAME -and $env:DB_NAME -ne 'campus_perks_db'){throw 'Unauthorized database.'}
$evidence=Join-Path $root '.local/business-tests'
if($Phase){
 if($Phase -notmatch '^[a-z0-9-]+$'){throw 'Invalid evidence phase name.'}
 $evidence=Join-Path $evidence $Phase
}
New-Item -ItemType Directory -Force -Path $evidence | Out-Null
if(Test-Path (Join-Path $evidence 'backend-requests.tsv')){throw 'Previous evidence exists. Archive it before another full run.'}
$settings=@{
 JAVA_HOME='E:\JDK17';DB_HOST='127.0.0.1';DB_PORT='3306';DB_NAME='campus_perks_db';DB_POOL_SIZE='3'
 CAMPUS_MYSQL_TEST='true';CAMPUS_EXTERNAL_TEST='false';CAMPUS_BUSINESS_EVIDENCE_DIR=$evidence
 CAMPUS_PROJECT_ROOT=$root;CAMPUS_MINIO_LIVE_TEST='false'
 CAMPUS_LIVE_BROWSER_TEST=$(if($TestSelection -match 'BusinessLive'){'true'}else{'false'})
 CAMPUS_LIVE_MAP_TEST=$(if($UseMapConfiguration){'true'}else{'false'})
 AI_ENABLED='false';ASSISTANT_ENABLED='false';MARKETING_AI_ENABLED='false';PINECONE_ENABLED='false'
 REDIS_ENABLED='false';MONGO_ENABLED='false';RABBITMQ_ENABLED='false';AMAP_ENABLED='false';MINIO_ENABLED='false'
 AUTH_ENABLED='false';JWT_ENABLED='false';SMS_SIMULATED_ENABLED='false';SEARCH_BACKEND='MYSQL'
 NOTIFICATION_DISPATCH_ENABLED='false';SEARCH_REBUILD_ENABLED='false';TRADE_SCHEDULER_ENABLED='false'
}
$saved=@{};$present=@{};$testExit=1
if($UseObjectStorageConfiguration){
 if(-not $env:MINIO_ENDPOINT -or -not $env:MINIO_PUBLIC_ENDPOINT -or -not $env:MINIO_ACCESS_KEY -or -not $env:MINIO_SECRET_KEY){throw 'Live storage testing requires private endpoints and application credentials.'}
 $settings.MINIO_ENABLED='true'
 $settings.CAMPUS_MINIO_LIVE_TEST='true'
}
if($UseMapConfiguration){
 if(-not $env:AMAP_JS_KEY -or -not $env:AMAP_SECURITY_CODE){throw 'Map test requires the existing private configuration.'}
 $settings.AMAP_ENABLED='true'
 if(-not $env:REDIS_URI){throw 'Live map proxy requires its configured Redis limiter.'}
 $settings.REDIS_ENABLED='true'
}
try {
 foreach($key in $settings.Keys){$present[$key]=Test-Path -LiteralPath ('Env:'+$key);$saved[$key]=[Environment]::GetEnvironmentVariable($key,'Process');[Environment]::SetEnvironmentVariable($key,$settings[$key],'Process')}
 $java=Join-Path $env:JAVA_HOME 'bin/java.exe'
 $driver='E:\maven-repository\com\mysql\mysql-connector-j\9.7.0\mysql-connector-j-9.7.0.jar'
 if(-not $SkipSnapshot){
   & $java --class-path $driver (Join-Path $PSScriptRoot 'BusinessDatabaseSnapshot.java') (Join-Path $evidence 'database-before.tsv')
   if($LASTEXITCODE -ne 0){throw 'Database baseline snapshot failed.'}
 }
 $communityFile=Join-Path $root 'backend/campus-boot/src/test/java/com/campus/perks/boot/CommunityStageFiveMySqlTest.java'
 $communityMethods=@([regex]::Matches((Get-Content -Raw $communityFile),'(?m)^\s*@Test\s*\r?\n\s*void\s+(\w+)\s*\(') | ForEach-Object {$_.Groups[1].Value})
 if($communityMethods.Count -ne 19){throw 'Review the complete community selection after test changes.'}
 $selection='AlipaySandboxAdapterTest,ElasticsearchSearchAdapterTest,SmsConfigurationTest,JwtValidationTest,RedemptionQrTest,ScaffoldContractTest,MySqlReadTest,AuthenticationMySqlTest,PasswordAuthenticationMySqlTest,RbacIsolationMySqlTest,MerchantStageTwoMySqlTest,CouponStageThreeMySqlTest,TradeStageFourMySqlTest,AnalyticsStageSevenMySqlTest,BusinessEndpointInventoryTest,BusinessReadChainsMySqlTest#business*,'+'CommunityStageFiveMySqlTest#'+($communityMethods -join '+')
 if($TestSelection){$selection=$TestSelection}
 $proxyArgs=if($UseLocalHttpProxy){@('-Dhttp.proxyHost=127.0.0.1','-Dhttp.proxyPort=7890','-Dhttps.proxyHost=127.0.0.1','-Dhttps.proxyPort=7890','-Dhttp.nonProxyHosts=127.*')}else{@()}
 & 'E:\apache-maven-3.8.8\bin\mvn.cmd' -o -B -fae -f (Join-Path $root 'backend/pom.xml') "-Dtest=$selection" '-Dsurefire.failIfNoSpecifiedTests=false' '-Djunit.jupiter.extensions.autodetection.enabled=true' @proxyArgs verify *> (Join-Path $evidence 'backend.log')
 $testExit=$LASTEXITCODE
 Write-Output "Business Maven exit code: $testExit; inspect XML assertions, not exit code alone."
} finally {
 if(-not $SkipSnapshot -and $java -and (Test-Path (Join-Path $evidence 'database-before.tsv'))){
   & $java --class-path $driver (Join-Path $PSScriptRoot 'BusinessDatabaseSnapshot.java') (Join-Path $evidence 'database-after.tsv')
   if($LASTEXITCODE -eq 0){
     $difference=Compare-Object (Get-Content (Join-Path $evidence 'database-before.tsv')) (Get-Content (Join-Path $evidence 'database-after.tsv'))
     ConvertTo-Json -InputObject @($difference) -Depth 3 | Set-Content (Join-Path $evidence 'database-differences.json')
     Write-Output ('Database digest difference entries: '+@($difference).Count)
   } else {Write-Warning 'Final snapshot failed; do not claim database preservation.'}
 }
 foreach($key in $saved.Keys){if($present[$key]){[Environment]::SetEnvironmentVariable($key,$saved[$key],'Process')}else{Remove-Item -LiteralPath ('Env:'+$key) -ErrorAction SilentlyContinue}}
}
exit $testExit
