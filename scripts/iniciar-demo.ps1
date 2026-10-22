# Arranca la aplicacion y un tunel HTTPS para probar notificaciones desde el celular.
# Las credenciales se leen de .env.demo (no versionado).
# Uso:  .\scripts\iniciar-demo.ps1

$ErrorActionPreference = 'Stop'
$raiz = Split-Path -Parent $PSScriptRoot
$archivoEnv = Join-Path $raiz '.env.demo'

if (Test-Path $archivoEnv) {
    Get-Content $archivoEnv | ForEach-Object {
        $linea = $_.Trim()
        if (-not $linea -or $linea.StartsWith('#')) { return }
        $partes = $linea.Split('=', 2)
        if ($partes.Count -eq 2) {
            $nombre = $partes[0].Trim()
            $valor = $partes[1].Trim().Trim('"').Trim("'")
            [Environment]::SetEnvironmentVariable($nombre, $valor, 'Process')
        }
    }
    Write-Host "Variables cargadas desde .env.demo" -ForegroundColor Green
} else {
    Write-Host "Aviso: no existe .env.demo; se usaran las variables de esta sesion." -ForegroundColor Yellow
}

if (-not $env:ALERTA_ADMIN_TOKEN) {
    Write-Host "Aviso: ALERTA_ADMIN_TOKEN esta vacio; la opcion 0 de la consola y los endpoints administrativos responderan 403." -ForegroundColor Yellow
}
if (-not $env:FIREBASE_CREDENTIALS_PATH -or -not (Test-Path $env:FIREBASE_CREDENTIALS_PATH)) {
    Write-Host "Aviso: FIREBASE_CREDENTIALS_PATH no apunta a un archivo existente." -ForegroundColor Yellow
}

Write-Host "Abriendo la aplicacion en una ventana nueva..." -ForegroundColor Cyan
Start-Process powershell -ArgumentList @(
    '-NoExit',
    '-Command',
    "Set-Location '$raiz'; .\mvnw.cmd spring-boot:run"
)

if (Get-Command cloudflared -ErrorAction SilentlyContinue) {
    Start-Sleep -Seconds 8
    Write-Host "Abriendo el tunel HTTPS. Copie la URL https://...trycloudflare.com que aparezca." -ForegroundColor Cyan
    Start-Process powershell -ArgumentList @(
        '-NoExit',
        '-Command',
        'cloudflared tunnel --url http://localhost:8081'
    )
} else {
    Write-Host "cloudflared no esta instalado. Instalelo con: winget install --id Cloudflare.cloudflared" -ForegroundColor Yellow
    Write-Host "Sin tunel, las notificaciones solo funcionan en esta PC (http://localhost:8081)." -ForegroundColor Yellow
}

Write-Host ""
Write-Host "Cuando la app termine de arrancar, abra http://localhost:8081 en la PC." -ForegroundColor Green
Write-Host "En el celular, abra la URL del tunel y pulse 'Activar notificaciones'." -ForegroundColor Green
Write-Host "Para enviar la prueba, use la opcion 0 en la consola del backend." -ForegroundColor Green
