param([switch]$SkipBuild,[switch]$BuildOnly,[ValidateSet('local','team')][string]$Profile='team',[ValidateSet('auto','h2','mysql')][string]$Database='auto')
$ErrorActionPreference='Stop'
$projectRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
Set-Location -LiteralPath $projectRoot
if (!$env:JAVA_HOME -or !(Test-Path -LiteralPath "$env:JAVA_HOME\bin\javac.exe")) {
    if (Test-Path -LiteralPath 'C:\Program Files\Java\jdk-17\bin\javac.exe') { $env:JAVA_HOME='C:\Program Files\Java\jdk-17' }
    else { throw 'JDK 17 is required. Set JAVA_HOME to its installation folder.' }
}
$javaExe=Join-Path $env:JAVA_HOME 'bin\java.exe'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
if (!$env:MAVEN_USER_HOME) { $env:MAVEN_USER_HOME=Join-Path $projectRoot '.maven' }
$mavenRepository=if($env:ORDERANDGO_MAVEN_REPO){$env:ORDERANDGO_MAVEN_REPO}else{Join-Path $projectRoot '.maven\repository'}
function Get-JarPath($name){return "$name\target\$name-0.1.0.jar"}
function Get-HealthPath($name){return '/actuator/health'}
$modules=[ordered]@{'user-service'=8081;'notification-service'=8083;'order-service'=8080}

foreach($port in $(if(!$BuildOnly){$modules.Values})) {
    if (Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue) {
        throw "Port $port is already in use. Stop this project with stop-local.cmd before rebuilding or restarting."
    }
}
if (!$SkipBuild) {
    & (Join-Path $projectRoot 'mvnw.cmd') -B --no-transfer-progress "-Dmaven.repo.local=$mavenRepository" package
    if ($LASTEXITCODE -ne 0) { throw 'Build or tests failed. Review the Maven output.' }
}
if ($BuildOnly) { return }
$mysqlConfigPath=Join-Path $projectRoot '.local\mysql.json'
if($Database -eq 'auto'){$Database=if(Test-Path -LiteralPath $mysqlConfigPath){'mysql'}else{'h2'}}
$activeProfiles=$Profile
$mysqlConfig=$null
if($Database -eq 'mysql'){
    if(!(Test-Path -LiteralPath $mysqlConfigPath)){throw 'Run setup-mysql.cmd once before using MySQL.'}
    $mysqlConfig=Get-Content -LiteralPath $mysqlConfigPath -Raw|ConvertFrom-Json
    if($mysqlConfig.schemaVersion -ne 2){throw 'Run setup-mysql.cmd -Upgrade to configure the three service databases.'}; $activeProfiles="$Profile,mysql"
}
foreach($entry in $modules.GetEnumerator()) {
    if (Get-NetTCPConnection -State Listen -LocalPort $entry.Value -ErrorAction SilentlyContinue) {
        throw "Port $($entry.Value) is already in use. Run stop-local.cmd if this project is already running."
    }
    $jar=Join-Path $projectRoot (Get-JarPath $entry.Key)
    if (!(Test-Path -LiteralPath $jar)) { throw "Missing $jar. Start without -SkipBuild first." }
}
$logDir=Join-Path $projectRoot '.logs'
New-Item -ItemType Directory -Path $logDir -Force | Out-Null
$started=@()
$previousMysqlEnv=@{}
foreach($name in @('MYSQL_HOST','MYSQL_PORT','MYSQL_USERNAME','MYSQL_PASSWORD')){$previousMysqlEnv[$name]=[Environment]::GetEnvironmentVariable($name,'Process')}
try {
    if($mysqlConfig){
        $env:MYSQL_HOST=$mysqlConfig.host;$env:MYSQL_PORT=[string]$mysqlConfig.port;$env:MYSQL_USERNAME=$mysqlConfig.username
        $mysqlSecret=ConvertTo-SecureString $mysqlConfig.protectedPassword
        $mysqlPointer=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($mysqlSecret)
        try{$env:MYSQL_PASSWORD=[Runtime.InteropServices.Marshal]::PtrToStringBSTR($mysqlPointer)}finally{[Runtime.InteropServices.Marshal]::ZeroFreeBSTR($mysqlPointer)}
    }
    foreach($entry in $modules.GetEnumerator()) {
        $name=$entry.Key
        $jar=Join-Path $projectRoot (Get-JarPath $name)
        $proc=Start-Process -FilePath $javaExe -ArgumentList @('-jar',('"'+$jar+'"'),"--spring.profiles.active=$activeProfiles","--server.port=$($entry.Value)","--server.address=127.0.0.1") -WorkingDirectory $projectRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $logDir "$name.log") -RedirectStandardError (Join-Path $logDir "$name.err.log")
        $started+=@{id=$proc.Id;name=$name;jar=(Get-JarPath $name);started=$proc.StartTime.ToUniversalTime().ToString('o');startedTicks=$proc.StartTime.ToUniversalTime().Ticks.ToString()}
        $started | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $logDir 'processes.json') -Encoding UTF8
    }
    $ready=$false
    for($attempt=0;$attempt -lt 90;$attempt++) {
        $allHealthy=$true
        foreach($entry in $modules.GetEnumerator()) {
            try { $health=Invoke-RestMethod -Uri "http://127.0.0.1:$($entry.Value)$(Get-HealthPath $entry.Key)" -TimeoutSec 2; if($entry.Key -notin @('menu-service','payment-service') -and $health.status -ne 'UP'){$allHealthy=$false} }
            catch { $allHealthy=$false }
        }
        if($allHealthy){$ready=$true;break}
        foreach($item in $started) { if(!(Get-Process -Id $item.id -ErrorAction SilentlyContinue)){throw "$($item.name) stopped. See .logs/$($item.name).log"} }
        Start-Sleep -Milliseconds 500
    }
    if(!$ready){throw 'Services did not become healthy. Check the .logs folder.'}

    Write-Host ''
    Write-Host 'ORDER & GO is ready: http://localhost:8080' -ForegroundColor Green
    Write-Host "Database: $Database"
    Write-Host 'Stop all project services with stop-local.cmd.'
} catch {
    foreach($item in $started) {
        $proc=Get-Process -Id $item.id -ErrorAction SilentlyContinue
        if($proc -and $proc.StartTime.ToUniversalTime().ToString('o') -eq $item.started) { Stop-Process -Id $item.id }
    }
    throw
} finally {
    foreach($name in $previousMysqlEnv.Keys){[Environment]::SetEnvironmentVariable($name,$previousMysqlEnv[$name],'Process')}
}


