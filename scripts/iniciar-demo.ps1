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

$puertoOcupado = Get-NetTCPConnection -LocalPort 8081 -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
if ($puertoOcupado) {
    Write-Host "El puerto 8081 ya esta en uso por el proceso $($puertoOcupado.OwningProcess)." -ForegroundColor Yellow
    Write-Host "Cierra esa instancia antes de ejecutar este script." -ForegroundColor Yellow
    Write-Host "Si no, el tunel nuevo quedaria apuntando a un backend que no autorizo su URL y el celular recibiria 403." -ForegroundColor Yellow
    exit 1
}

$urlTunel = $null
if (Get-Command cloudflared -ErrorAction SilentlyContinue) {
    $carpetaTarget = Join-Path $raiz 'target'
    New-Item -ItemType Directory -Force -Path $carpetaTarget | Out-Null
    $logOut = Join-Path $carpetaTarget 'cloudflared-demo.out.log'
    $logErr = Join-Path $carpetaTarget 'cloudflared-demo.err.log'
    Remove-Item $logOut, $logErr -ErrorAction SilentlyContinue

    Write-Host "Abriendo el tunel HTTPS..." -ForegroundColor Cyan
    Start-Process -FilePath 'cloudflared' `
        -ArgumentList @('tunnel', '--url', 'http://localhost:8081', '--no-autoupdate') `
        -RedirectStandardOutput $logOut -RedirectStandardError $logErr `
        -WindowStyle Hidden | Out-Null

    for ($intento = 0; $intento -lt 40 -and -not $urlTunel; $intento++) {
        Start-Sleep -Milliseconds 500
        $contenido = @()
        if (Test-Path $logOut) { $contenido += Get-Content $logOut -Raw -ErrorAction SilentlyContinue }
        if (Test-Path $logErr) { $contenido += Get-Content $logErr -Raw -ErrorAction SilentlyContinue }
        $coincidencia = [regex]::Match(($contenido -join "`n"), 'https://[a-z0-9-]+\.trycloudflare\.com')
        if ($coincidencia.Success) { $urlTunel = $coincidencia.Value }
    }

    if ($urlTunel) {
        $origenes = @($env:ALERTA_CORS_ORIGENES -split ',' |
            Where-Object { $_ -and $_ -notmatch '^https://[a-z0-9-]+\.trycloudflare\.com$' })
        if ($origenes -notcontains $urlTunel) { $origenes += $urlTunel }
        $env:ALERTA_CORS_ORIGENES = ($origenes -join ',')
        Write-Host "URL del tunel: $urlTunel" -ForegroundColor Green
        Write-Host "Origen agregado a ALERTA_CORS_ORIGENES para el celular." -ForegroundColor Gray
    } else {
        Write-Host "No se pudo leer la URL del tunel. Revise $logErr" -ForegroundColor Yellow
    }
} else {
    Write-Host "cloudflared no esta instalado. Instalelo con: winget install --id Cloudflare.cloudflared" -ForegroundColor Yellow
    Write-Host "Sin tunel, las notificaciones solo funcionan en esta PC (http://localhost:8081)." -ForegroundColor Yellow
}

Write-Host "Abriendo la aplicacion en una ventana nueva..." -ForegroundColor Cyan
Start-Process powershell -ArgumentList @(
    '-NoExit',
    '-Command',
    "Set-Location '$raiz'; .\mvnw.cmd spring-boot:run"
)

Write-Host ""
Write-Host "Cuando la app termine de arrancar, abra http://localhost:8081 en la PC." -ForegroundColor Green
if ($urlTunel) {
    Write-Host "En el celular, abra: $urlTunel" -ForegroundColor Green
    Write-Host "Inicie sesion como admin, active las notificaciones y use 'Simular crecida'." -ForegroundColor Green
} else {
    Write-Host "En el celular, use la URL HTTPS de un tunel; sin HTTPS no hay notificaciones." -ForegroundColor Yellow
}
Write-Host "En la consola, use la opcion 6 para simular una crecida o la 7 para la demo global." -ForegroundColor Green
