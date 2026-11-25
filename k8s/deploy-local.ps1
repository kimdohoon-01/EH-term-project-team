$ErrorActionPreference = 'Stop'
$cluster = 'docker-desktop'
$projectNamespace = 'cafe-pickup'

function Invoke-Kubectl {
    & kubectl --context $cluster @args
    if ($LASTEXITCODE -ne 0) {
        throw '쿠버네티스 명령이 실패했습니다. 위 오류를 확인해 주세요.'
    }
}

Invoke-Kubectl --request-timeout=10s get nodes
Invoke-Kubectl apply -f (Join-Path $PSScriptRoot 'namespace.yaml')

function Initialize-Secret([string]$name, [hashtable]$values) {
    $existing = Invoke-Kubectl -n $projectNamespace get secret $name --ignore-not-found -o name
    if (-not $existing) {
        $secretManifest = @{
            apiVersion = 'v1'
            kind = 'Secret'
            metadata = @{ name = $name }
            stringData = $values
        } | ConvertTo-Json -Depth 4 -Compress
        $secretManifest | & kubectl --context $cluster -n $projectNamespace create -f -
        if ($LASTEXITCODE -ne 0) {
            throw "$name Secret을 만들지 못했습니다."
        }
    }
}

$databases = @('menu-db', 'payment-db', 'user-db', 'order-db', 'notification-db')
$apps = @('menu-service', 'payment-service', 'user-service', 'notification-service', 'order-service', 'cafe-gateway')

foreach ($database in $databases) {
    Initialize-Secret "$database-credentials" @{
        password = [guid]::NewGuid().ToString('N')
        'root-password' = [guid]::NewGuid().ToString('N')
    }
}
Initialize-Secret 'orderandgo-internal' @{ 'api-key' = [guid]::NewGuid().ToString('N') }

foreach ($database in $databases) {
    Invoke-Kubectl -n $projectNamespace apply -f (Join-Path $PSScriptRoot "$database.yaml")
}
foreach ($deployment in $databases) {
    Invoke-Kubectl -n $projectNamespace rollout status "deployment/$deployment" --timeout=180s
}

Invoke-Kubectl apply -k $PSScriptRoot
foreach ($deployment in $apps) {
    Invoke-Kubectl -n $projectNamespace rollout status "deployment/$deployment" --timeout=180s
}
Invoke-Kubectl -n $projectNamespace wait '--for=jsonpath={.status.loadBalancer.ingress}' service/cafe-gateway --timeout=120s
Invoke-Kubectl -n $projectNamespace get 'pods,services,pvc'
