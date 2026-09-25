# Envía cada elemento de data/webhooks.json como un POST /aplicaciones, igual que lo haría el emisor real.
# Uso: .\scripts\enviar-webhooks.ps1 [-Url http://localhost:8080] [-Archivo data\webhooks.json]
param(
    [string]$Url = "http://localhost:8080",
    [string]$Archivo = (Join-Path $PSScriptRoot "..\data\webhooks.json")
)

$webhooks = Get-Content -Raw -Encoding UTF8 $Archivo | ConvertFrom-Json
foreach ($webhook in $webhooks) {
    $cuerpo = [System.Text.Encoding]::UTF8.GetBytes(($webhook | ConvertTo-Json -Depth 10 -Compress))
    $respuesta = Invoke-WebRequest -Uri "$Url/aplicaciones" -Method Post -Body $cuerpo `
        -ContentType "application/json; charset=utf-8" -UseBasicParsing
    Write-Output ("{0}  HTTP {1}  {2}" -f $webhook.submission_id, $respuesta.StatusCode, $respuesta.Content)
}
