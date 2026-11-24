param([int]$Port=8082)
$ErrorActionPreference='Stop'
$base="http://127.0.0.1:$Port/api/menus"
$menus=Invoke-RestMethod "${base}?includeInactive=true"
if(@($menus).Count -gt 0){Write-Host 'Menus already exist; no demo data added.';return}
$data=Get-Content (Join-Path $PSScriptRoot '../docs/demo-menu.json') -Raw -Encoding UTF8|ConvertFrom-Json
foreach($menu in $data){Invoke-RestMethod $base -Method Post -ContentType 'application/json' -Body ([Text.Encoding]::UTF8.GetBytes(($menu|ConvertTo-Json -Compress)))|Out-Null}
Write-Host 'Six demo menus added through the menu service API.'
