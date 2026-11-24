# Starts isolated services with in-memory databases; leaves the normal app data intact.
$ErrorActionPreference='Stop'
$projectRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$testLogs=Join-Path $projectRoot '.logs\smoke-test'
New-Item -ItemType Directory -Path $testLogs -Force | Out-Null
$javaExe=if($env:JAVA_HOME){Join-Path $env:JAVA_HOME 'bin\java.exe'}else{'C:\Program Files\Java\jdk-17\bin\java.exe'}
$modules=[ordered]@{'user-service'=19081;'notification-service'=19083;'menu-service'=19082;'payment-service'=19084;'order-service'=19080}
$processes=@()
$checks=0
function Assert-That($condition,[string]$message){if(!$condition){throw "FAILED: $message"};$script:checks++;Write-Host "PASS $message"}
function Request([string]$path,[string]$method='GET',$body=$null,$headers=@{}){
    $parameters=@{Uri="http://127.0.0.1:19080/api$path";Method=$method;Headers=$headers;ContentType='application/json';TimeoutSec=15}
    if($null -ne $body){$parameters.Body=[Text.Encoding]::UTF8.GetBytes(($body|ConvertTo-Json -Depth 10 -Compress))}
    Invoke-RestMethod @parameters
}
function Expect-Status([scriptblock]$action,[int]$expected,[string]$message){
    $actual=0
    try{& $action | Out-Null}catch{if($_.Exception.Response){$actual=[int]$_.Exception.Response.StatusCode}else{throw}}
    Assert-That ($actual -eq $expected) "$message (HTTP $expected)"
}
try{
    foreach($entry in $modules.GetEnumerator()){
        if(Get-NetTCPConnection -LocalPort $entry.Value -State Listen -ErrorAction SilentlyContinue){throw "Test port $($entry.Value) in use"}
        $jar=if($entry.Key -in @('menu-service','payment-service')){Join-Path $projectRoot "..\$($entry.Key)\target\$($entry.Key).jar"}else{Join-Path $projectRoot "$($entry.Key)\target\$($entry.Key)-0.1.0.jar"}
        $arguments=@('-jar',('"'+$jar+'"'),"--server.port=$($entry.Value)",'--spring.datasource.url=jdbc:h2:mem:smoke;MODE=MySQL;DB_CLOSE_DELAY=-1','--spring.profiles.active=local')
        if($entry.Key -eq 'order-service'){$arguments+=@('--services.user.url=http://127.0.0.1:19081','--services.notification.url=http://127.0.0.1:19083','--services.menu.url=http://127.0.0.1:19082','--services.payment.url=http://127.0.0.1:19084')}
        $processes+=Start-Process -FilePath $javaExe -ArgumentList $arguments -WorkingDirectory $projectRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $testLogs "$($entry.Key).log") -RedirectStandardError (Join-Path $testLogs "$($entry.Key).err.log")
    }
    $ready=$false
    for($attempt=0;$attempt -lt 80;$attempt++){
        $healthy=$true
        foreach($entry in $modules.GetEnumerator()){$path=if($entry.Key -eq 'menu-service'){'/api/menus'}elseif($entry.Key -eq 'payment-service'){'/api/payments'}else{'/actuator/health'};try{Invoke-RestMethod "http://127.0.0.1:$($entry.Value)$path" -TimeoutSec 1|Out-Null}catch{$healthy=$false}}
        if($healthy){$ready=$true;break};Start-Sleep -Milliseconds 500
    }
    if(!$ready){throw 'Test services failed to start; see .logs/smoke-test.'}
    & (Join-Path $PSScriptRoot 'seed-demo-menu.ps1') -Port 19082
    Expect-Status {Request '/auth/register' 'POST' @{email='weak@example.test';password='password123';name='Tester';address='Seoul Jung-gu 110'}} 400 'weak registration password rejected by server'
    Expect-Status {Request '/auth/register' 'POST' @{email='noaddress@example.test';password='Test-password-123!';name='Tester';address=' '}} 400 'blank address rejected by server'
    $registered=Request '/auth/register' 'POST' @{email='first@example.test';password='Test-password-123!';name='First User';address='Seoul Jung-gu 110';addressDetail='101'}
    Assert-That ($null -ne $registered.accessToken) 'registration through gateway'
    $loggedIn=Request '/auth/login' 'POST' @{email='FIRST@example.test';password='Test-password-123!'}
    $auth=@{Authorization="Bearer $($loggedIn.accessToken)"}
    Assert-That ((Request '/auth/me' 'GET' $null $auth).id -eq $registered.user.id) 'login and current user'
    Expect-Status {Request '/orders'} 401 'anonymous orders rejected'
    Expect-Status {Request '/auth/login' 'POST' @{email='first@example.test';password='wrong'}} 401 'wrong password rejected'
    $before=Request '/menus'
    Assert-That (@($before).Count -eq 6) 'menus initialized before accepting requests'
    $initialStock=($before|Where-Object id -eq '1').stock
    $orderHeaders=$auth.Clone();$orderHeaders['Idempotency-Key']='smoke-success-001'
    $body=@{items=@(@{menuId='1';quantity=2});paymentMethod='CARD'}
    $paid=Request '/orders' 'POST' $body $orderHeaders
    Assert-That ($paid.status -eq 'CONFIRMED' -and $paid.total -eq 7000) 'payment success confirms server-priced order'
    Assert-That (((Request '/menus')|Where-Object id -eq '1').stock -eq $initialStock-2) 'stock deducted once'
    $duplicate=Request '/orders' 'POST' $body $orderHeaders
    Assert-That ($duplicate.id -eq $paid.id -and ((Request '/menus')|Where-Object id -eq '1').stock -eq $initialStock-2) 'duplicate order key does not double charge or deduct'
    $different=@{items=@(@{menuId='1';quantity=3});paymentMethod='CARD'}
    Expect-Status {Request '/orders' 'POST' $different $orderHeaders} 409 'reused key with different payload rejected'
    $orderHeaders['Idempotency-Key']='smoke-decline-001'
    $declined=Request '/orders' 'POST' @{items=@(@{menuId='1';quantity=1});paymentMethod='TEST_DECLINE'} $orderHeaders
    Assert-That ($declined.status -eq 'CANCELLED') 'payment failure cancels order'
    Assert-That (((Request '/menus')|Where-Object id -eq '1').stock -eq $initialStock-2) 'failed payment restores inventory'
    $orderHeaders['Idempotency-Key']='smoke-shortage-001'
    $short=Request '/orders' 'POST' @{items=@(@{menuId='6';quantity=20});paymentMethod='CARD'} $orderHeaders
    Assert-That ($short.status -eq 'CANCELLED' -and ((Request '/menus')|Where-Object id -eq '6').stock -eq 10) 'insufficient stock cancels without changing stock'
    Assert-That ((Invoke-RestMethod "http://127.0.0.1:19084/api/payments/$($paid.id)").status -eq 'SUCCEEDED') 'Gyua payment success persisted'
    Assert-That ((Invoke-RestMethod "http://127.0.0.1:19084/api/payments/$($declined.id)").status -eq 'FAILED') 'Gyua payment failure persisted'
    Assert-That ((Invoke-RestMethod "http://127.0.0.1:19082/api/stock/reservations/$($declined.id)").status -eq 'RELEASED') 'Gyua reservation released'
    Invoke-RestMethod "http://127.0.0.1:19082/api/stock/reservations/$($declined.id)/release" -Method Post|Out-Null
    Assert-That (((Request '/menus')|Where-Object id -eq '1').stock -eq $initialStock-2) 'repeated release does not increase stock twice'
    Expect-Status {Invoke-RestMethod "http://127.0.0.1:19084/api/payments/$($short.id)"} 404 'shortage never creates payment'
    $notifications=Request '/notifications' 'GET' $null $auth
    Assert-That ($notifications.Count -eq 3) "final order notifications delivered (count=$($notifications.Count))"
    Request "/notifications/$($notifications[0].id)/read" 'PATCH' $null $auth | Out-Null
    $afterRead=Request '/notifications' 'GET' $null $auth
    Assert-That (@($afterRead|Where-Object {$_.read -eq $true}).Count -eq 1) 'notification read state saved'
    $other=Request '/auth/register' 'POST' @{email='second@example.test';password='Test-password-123!';name='Other User';address='Seoul Jung-gu 110'}
    $otherAuth=@{Authorization="Bearer $($other.accessToken)"}
    Expect-Status {Request "/orders/$($paid.id)" 'GET' $null $otherAuth} 404 'another user cannot read order'
    Expect-Status {Request "/notifications/$($notifications[0].id)/read" 'PATCH' $null $otherAuth} 404 'another user cannot mark notification read'
    Expect-Status {Invoke-RestMethod 'http://127.0.0.1:19083/internal/notifications?userId=unknown'} 403 'internal API requires service key'
    $orderHeaders['Idempotency-Key']='smoke-invalid-001'
    Expect-Status {Request '/orders' 'POST' @{items=@(@{menuId='1';quantity=0});paymentMethod='CARD'} $orderHeaders} 400 'invalid quantity rejected'
    Request '/auth/session' 'DELETE' $null $auth | Out-Null
    Expect-Status {Request '/auth/me' 'GET' $null $auth} 401 'logout revokes token'
    Write-Host "$checks integration checks passed." -ForegroundColor Green
}finally{
    foreach($proc in $processes){if(!$proc.HasExited){Stop-Process -Id $proc.Id -ErrorAction SilentlyContinue}}
}
