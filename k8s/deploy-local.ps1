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

foreach ($database in @('menu-db', 'payment-db')) {
    $secret = "$database-credentials"
    $existing = Invoke-Kubectl -n $projectNamespace get secret $secret --ignore-not-found -o name
    if (-not $existing) {
        $password = [guid]::NewGuid().ToString('N')
        $rootPassword = [guid]::NewGuid().ToString('N')
        $secretManifest = @{
            apiVersion = 'v1'
            kind = 'Secret'
            metadata = @{ name = $secret }
            stringData = @{ password = $password; 'root-password' = $rootPassword }
        } | ConvertTo-Json -Depth 4 -Compress
        $secretManifest | & kubectl --context $cluster -n $projectNamespace create -f -
        if ($LASTEXITCODE -ne 0) {
            throw "$database DB Secret을 만들지 못했습니다."
        }
    }
}

Invoke-Kubectl -n $projectNamespace apply -f (Join-Path $PSScriptRoot 'menu-db.yaml') -f (Join-Path $PSScriptRoot 'payment-db.yaml')
foreach ($deployment in @('menu-db', 'payment-db')) {
    Invoke-Kubectl -n $projectNamespace rollout status "deployment/$deployment" --timeout=180s
}

Invoke-Kubectl -n $projectNamespace apply -f (Join-Path $PSScriptRoot 'menu-service.yaml') -f (Join-Path $PSScriptRoot 'payment-service.yaml')
foreach ($deployment in @('menu-service', 'payment-service')) {
    Invoke-Kubectl -n $projectNamespace rollout status "deployment/$deployment" --timeout=180s
}
Invoke-Kubectl -n $projectNamespace apply -f (Join-Path $PSScriptRoot 'cafe-gateway.yaml')
Invoke-Kubectl -n $projectNamespace rollout status deployment/cafe-gateway --timeout=180s
Invoke-Kubectl -n $projectNamespace wait '--for=jsonpath={.status.loadBalancer.ingress}' service/cafe-gateway --timeout=120s
Invoke-Kubectl -n $projectNamespace get 'pods,services,pvc'
