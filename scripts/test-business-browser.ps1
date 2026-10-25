param([switch]$RecordRoutes,[string]$Phase='')
$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$evidence=Join-Path $root '.local/business-tests'
if($Phase){if($Phase -notmatch '^[a-z0-9-]+$'){throw 'Invalid evidence phase.'};$evidence=Join-Path $evidence $Phase}
New-Item -ItemType Directory -Force -Path $evidence | Out-Null
if($RecordRoutes){& node (Join-Path $root 'frontend/scripts/route-inventory.mjs') (Join-Path $evidence 'frontend-routes.json');if($LASTEXITCODE -ne 0){throw 'Route inventory export failed.'}}
if(-not $env:CAMPUS_PLAYWRIGHT_MODULE){$env:CAMPUS_PLAYWRIGHT_MODULE='C:\Users\rains\.cache\codex-runtimes\codex-primary-runtime\dependencies\node\node_modules\playwright'}
if(-not $env:CAMPUS_BROWSER_EXECUTABLE){$env:CAMPUS_BROWSER_EXECUTABLE='C:\Program Files\Google\Chrome\Application\chrome.exe'}
$env:CHROME_PATH=$env:CAMPUS_BROWSER_EXECUTABLE
$scripts=@('auth','password-map','rbac-business','merchant','coupon','trade','community','analytics')
$results=@()
foreach($name in $scripts){
 $script=Join-Path $root "frontend/scripts/$name-browser-check.mjs"
 $log=Join-Path $evidence "browser-$name.log"
 $started=Get-Date
 if($RecordRoutes){
   $env:CAMPUS_UI_ROUTE_EVIDENCE_DIR=$evidence
   $env:CAMPUS_UI_EVIDENCE_SUITE=$name
   $previous=Join-Path $evidence "browser-$name-before-route-audit.log"
   if((Test-Path $log) -and -not (Test-Path $previous)){Copy-Item -LiteralPath $log -Destination $previous}
   $hookUrl=[System.Uri]::new((Join-Path $root 'frontend/scripts/browser-route-evidence-hook.mjs')).AbsoluteUri
   & node --import $hookUrl $script *> $log
 } else { & node $script *> $log }
 $exit=$LASTEXITCODE
 $results+=[pscustomobject]@{suite=$name;exitCode=$exit;evidenceLevel='F';elapsedSeconds=[math]::Round(((Get-Date)-$started).TotalSeconds,2);log=(Split-Path $log -Leaf)}
 $results | ConvertTo-Json -Depth 3 | Set-Content (Join-Path $evidence 'browser-results.json')
 Write-Output "Browser $name exit code: $exit"
}
if(@($results | Where-Object exitCode -ne 0).Count -gt 0){exit 1}
exit 0
