$ErrorActionPreference='Stop'
$projectRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$pidFile=Join-Path $projectRoot '.logs\processes.json'
if(!(Test-Path -LiteralPath $pidFile)){Write-Host 'No recorded project services.';return}
$items=Get-Content -LiteralPath $pidFile -Raw | ConvertFrom-Json
foreach($item in $items){
    $proc=Get-Process -Id $item.id -ErrorAction SilentlyContinue
    if(!$proc){continue}
    $recordedTicks=if($item.startedTicks){[long]$item.startedTicks}else{([DateTimeOffset]$item.started).UtcDateTime.Ticks}
    if($proc.ProcessName -notlike 'java*' -or $proc.StartTime.ToUniversalTime().Ticks -ne $recordedTicks){continue}
    $details=Get-CimInstance Win32_Process -Filter "ProcessId=$($item.id)"
    $relativeJar=switch ($item.name) {
        'menu-service' { '..\menu-service\target\menu-service.jar' }
        'payment-service' { '..\payment-service\target\payment-service.jar' }
        'user-service' { 'user-service\target\user-service-0.1.0.jar' }
        'notification-service' { 'notification-service\target\notification-service-0.1.0.jar' }
        'order-service' { 'order-service\target\order-service-0.1.0.jar' }
    }
    if (!$relativeJar) { continue }
    $expectedJar=[IO.Path]::GetFullPath((Join-Path $projectRoot $relativeJar))
    if($details.CommandLine -and $details.CommandLine.Contains($expectedJar)){
        Stop-Process -Id $item.id
        Write-Host "Stopped $($item.name)."
    }
}
