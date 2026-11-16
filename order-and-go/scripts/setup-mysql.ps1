param(
    [ValidateSet('127.0.0.1','localhost')][string]$DbHost='127.0.0.1',
    [ValidateRange(1,65535)][int]$Port=3306,
    [string]$AdminUser='root',
    [Security.SecureString]$AdminPassword,
    [switch]$SkipBuild,
    [switch]$Upgrade,[switch]$NoStart
)
$ErrorActionPreference='Stop'
$projectRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$privateDir=Join-Path $projectRoot '.local'
$configPath=Join-Path $privateDir 'mysql.json'
if((Test-Path -LiteralPath $configPath) -and !$Upgrade){throw 'MySQL is already configured. Use stop-local.cmd then start-local.cmd.'}
if(!$env:JAVA_HOME -or !(Test-Path -LiteralPath "$env:JAVA_HOME\bin\javac.exe")){
    if(Test-Path -LiteralPath 'C:\Program Files\Java\jdk-17\bin\javac.exe'){$env:JAVA_HOME='C:\Program Files\Java\jdk-17'}
    else{throw 'JDK 17 is required. Set JAVA_HOME first.'}
}
Write-Host "Configure MySQL at ${DbHost}:$Port. Enter the same administrator password used in HeidiSQL."
Write-Host 'The root password will not be saved. A separate project account will be created.'
if($null -eq $AdminPassword){$AdminPassword=Read-Host 'MySQL administrator password (hidden)' -AsSecureString}
& (Join-Path $PSScriptRoot 'stop-local.ps1')
if(!$SkipBuild){& (Join-Path $PSScriptRoot 'start-local.ps1') -BuildOnly -Database h2}
$jar=Join-Path $projectRoot 'user-service\target\user-service-0.1.0.jar'
if(!(Test-Path -LiteralPath $jar)){throw 'Build the project first.'}
New-Item -ItemType Directory -Path $privateDir -Force | Out-Null
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive=[IO.Compression.ZipFile]::OpenRead($jar)
$driverPath=Join-Path $privateDir 'mysql-connector-j.jar'
try{
    $entry=$archive.Entries | Where-Object {$_.FullName -match '^BOOT-INF/lib/mysql-connector-j-[^/]+\.jar$'}
    if(!$entry){throw 'The MySQL driver is missing. Rebuild without -SkipBuild.'}
    [IO.Compression.ZipFileExtensions]::ExtractToFile($entry,$driverPath,$true)
}finally{$archive.Dispose()}
$java=Join-Path $env:JAVA_HOME 'bin\java.exe'
$helper=Join-Path $PSScriptRoot 'SetupMysql.java'
$info=New-Object Diagnostics.ProcessStartInfo
$info.FileName=$java
$info.Arguments='-cp "'+$driverPath+'" "'+$helper+'" "'+$projectRoot+'" '+$DbHost+' '+$Port+' "'+$AdminUser.Replace('"','')+'"'
$info.UseShellExecute=$false
$info.CreateNoWindow=$true
$info.RedirectStandardOutput=$true
$info.RedirectStandardError=$true
$secretPointer=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($AdminPassword)
$process=$null
try{
    $info.EnvironmentVariables['ORDERANDGO_ADMIN_PASSWORD']=[Runtime.InteropServices.Marshal]::PtrToStringBSTR($secretPointer)
    $process=[Diagnostics.Process]::Start($info)
}finally{
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($secretPointer)
    $info.EnvironmentVariables.Remove('ORDERANDGO_ADMIN_PASSWORD')
}
try{
    $resultTask=$process.StandardOutput.ReadToEndAsync()
    $errorTask=$process.StandardError.ReadToEndAsync()
    if(!$process.WaitForExit(60000)){$process.Kill();throw 'MySQL setup timed out. Check the MySQL service.'}
    $result=$resultTask.GetAwaiter().GetResult()
    $safeError=$errorTask.GetAwaiter().GetResult()
    if($process.ExitCode -ne 0){throw $safeError}
    $account=$result|ConvertFrom-Json
    $protectedPassword=ConvertTo-SecureString $account.password -AsPlainText -Force | ConvertFrom-SecureString
    $config=@{schemaVersion=2;host=$DbHost;port=$Port;username=$account.username;protectedPassword=$protectedPassword}
    if(Test-Path -LiteralPath $configPath){Copy-Item -LiteralPath $configPath -Destination ($configPath+'.backup-'+(Get-Date -Format yyyyMMddHHmmss))}; $config|ConvertTo-Json|Set-Content -LiteralPath $configPath -Encoding UTF8
    $account=$null;$result=$null
}finally{if($process){$process.Dispose()}}
Write-Host 'MySQL databases and tables are ready.' -ForegroundColor Green
Write-Host 'Refresh HeidiSQL: orderandgo_user, orderandgo_order, orderandgo_notification.'
Write-Host 'The project account password is encrypted for this Windows user in .local/mysql.json.'
if(!$NoStart){& (Join-Path $PSScriptRoot 'start-local.ps1') -SkipBuild -Database mysql}


