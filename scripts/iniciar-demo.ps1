# Arranca backend, frontend React/Vite y tunel HTTPS para la demo.
# Las credenciales se leen de .env.demo (no versionado).
# Uso:  .\scripts\iniciar-demo.ps1 [-SinTunel] [-SinNavegador]

param(
    [switch]$SinTunel,
    [switch]$SinNavegador
)

$ErrorActionPreference = 'Stop'
$raiz = Split-Path -Parent $PSScriptRoot
$archivoEnv = Join-Path $raiz '.env.demo'
$dirLogs = Join-Path $raiz 'target\logs'
New-Item -ItemType Directory -Force -Path $dirLogs | Out-Null

function Escribir-Info($texto) { Write-Host "[INFO] $texto" -ForegroundColor Cyan }
function Escribir-Ok($texto) { Write-Host "[OK] $texto" -ForegroundColor Green }
function Escribir-Aviso($texto) { Write-Host "[AVISO] $texto" -ForegroundColor Yellow }
function Escribir-Error($texto) { Write-Host "[ERROR] $texto" -ForegroundColor Red }

function Cargar-EnvDemo {
    if (-not (Test-Path $archivoEnv)) {
        Escribir-Aviso "No existe .env.demo; se usaran las variables de esta sesion."
        return
    }
    Get-Content $archivoEnv | ForEach-Object {
        $linea = $_.Trim()
        if (-not $linea -or $linea.StartsWith('#')) { return }
        $partes = $linea.Split('=', 2)
        if ($partes.Count -ne 2) { return }
        $nombre = $partes[0].Trim()
        $valor = $partes[1].Trim()
        if ($valor.Length -ge 2) {
            $primero = $valor.Substring(0, 1)
            $ultimo = $valor.Substring($valor.Length - 1, 1)
            if (($primero -eq '"' -and $ultimo -eq '"') -or ($primero -eq "'" -and $ultimo -eq "'")) {
                $valor = $valor.Substring(1, $valor.Length - 2)
            }
        }
        [Environment]::SetEnvironmentVariable($nombre, $valor, 'Process')
    }
    Escribir-Ok "Variables cargadas desde .env.demo"
}

function Probar-Url($url) {
    try {
        $respuesta = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 5
        return $respuesta.StatusCode -ge 200 -and $respuesta.StatusCode -lt 400
    } catch {
        return $false
    }
}

function Esperar-Url($url, $intentos, $milisegundos) {
    for ($i = 0; $i -lt $intentos; $i++) {
        if (Probar-Url $url) { return $true }
        Start-Sleep -Milliseconds $milisegundos
    }
    return $false
}

function Iniciar-Servicio($nombre, $archivo, $argumentos, $directorio, $prefijoLog) {
    $logOut = Join-Path $dirLogs "$prefijoLog.out.log"
    $logErr = Join-Path $dirLogs "$prefijoLog.err.log"
    Remove-Item $logOut, $logErr -Force -ErrorAction SilentlyContinue
    $proceso = Start-Process -FilePath $archivo `
        -ArgumentList $argumentos `
        -WorkingDirectory $directorio `
        -RedirectStandardOutput $logOut `
        -RedirectStandardError $logErr `
        -WindowStyle Hidden `
        -PassThru
    return [pscustomobject]@{
        Nombre = $nombre
        Id = $proceso.Id
        LogOut = $logOut
        LogErr = $logErr
    }
}

function Detener-Servicio($servicio) {
    if (-not $servicio) { return }
    try {
        & taskkill.exe /PID $servicio.Id /T /F 2>$null | Out-Null
    } catch {
        # El proceso ya pudo haber terminado.
    }
}

function Mostrar-Log($servicio, $lineas) {
    if (-not $servicio) { return }
    foreach ($archivo in @($servicio.LogOut, $servicio.LogErr)) {
        if (Test-Path $archivo) {
            Write-Host "--- $archivo ---" -ForegroundColor DarkGray
            Get-Content $archivo -Tail $lineas -ErrorAction SilentlyContinue
        }
    }
}

$iniciados = @()
$codigoSalida = 0

try {
    Escribir-Info "Sistema de Alerta Temprana de Inundaciones - Lanzador Windows"
    Cargar-EnvDemo

    # La ruta de Firebase se interpreta relativa a la raiz del proyecto.
    if ($env:FIREBASE_CREDENTIALS_PATH -and -not [System.IO.Path]::IsPathRooted($env:FIREBASE_CREDENTIALS_PATH)) {
        $env:FIREBASE_CREDENTIALS_PATH = Join-Path $raiz $env:FIREBASE_CREDENTIALS_PATH
    }
    # El lanzador no necesita la consola interactiva; la demo se hace desde la web.
    $env:ALERTA_DEMO_CONSOLA_HABILITADA = 'false'

    if (-not $env:ALERTA_CORS_ORIGENES) {
        $env:ALERTA_CORS_ORIGENES = 'http://localhost:3000,http://localhost:5173,http://localhost:8081,http://127.0.0.1:8081'
    }
    if (-not $env:ALERTA_ADMIN_TOKEN) {
        Escribir-Aviso "ALERTA_ADMIN_TOKEN vacio; solo afecta a las rutas heredadas."
    }
    if ($env:FIREBASE_ENABLED -ne 'false' -and (-not $env:FIREBASE_CREDENTIALS_PATH -or -not (Test-Path $env:FIREBASE_CREDENTIALS_PATH))) {
        Escribir-Aviso "FIREBASE_CREDENTIALS_PATH no apunta a un archivo existente; FCM quedara deshabilitado."
    }

    if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
        throw "Java no esta instalado o no esta en el PATH."
    }
    if (-not (Get-Command node -ErrorAction SilentlyContinue) -or -not (Get-Command npm -ErrorAction SilentlyContinue)) {
        throw "Node.js o npm no estan instalados."
    }

    # Dependencias del frontend: se instalan si faltan o si node_modules vino
    # de otra plataforma (por ejemplo, sincronizado desde Linux con OneDrive).
    $viteCmd = Join-Path $raiz 'frontend\node_modules\.bin\vite.cmd'
    $rolldownWin = Get-ChildItem (Join-Path $raiz 'frontend\node_modules\@rolldown') `
        -Filter 'binding-win32-*' -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not (Test-Path $viteCmd) -or -not $rolldownWin) {
        Escribir-Aviso "Faltan dependencias del frontend o vienen de otra plataforma; ejecutando npm install..."
        Push-Location (Join-Path $raiz 'frontend')
        try {
            & npm install
            if ($LASTEXITCODE -ne 0) { throw "npm install fallo con codigo $LASTEXITCODE." }
        } finally {
            Pop-Location
        }
        Escribir-Ok "Dependencias del frontend instaladas."
    } else {
        Escribir-Ok "Dependencias del frontend presentes."
    }

    # Si target/ se sincronizo desde Linux con copias *-safeBackup-*, Maven
    # puede fallar con "Unable to find a single main class".
    $clasesBackup = Get-ChildItem (Join-Path $raiz 'target\classes') `
        -Filter '*safeBackup*' -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($clasesBackup) {
        Escribir-Aviso "Se detectaron clases duplicadas de una sincronizacion anterior; limpiando target/..."
        Push-Location $raiz
        try {
            & cmd.exe /c 'mvnw.cmd -q clean'
            if ($LASTEXITCODE -ne 0) { throw "mvnw.cmd clean fallo con codigo $LASTEXITCODE." }
        } finally {
            Pop-Location
        }
        New-Item -ItemType Directory -Force -Path $dirLogs | Out-Null
        Escribir-Ok "target/ limpio."
    }

    # PostgreSQL debe estar escuchando en 5432.
    $pg = Get-NetTCPConnection -LocalPort 5432 -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $pg) {
        Escribir-Aviso "PostgreSQL no responde en 5432. Intentando iniciar el servicio..."
        $servicioPg = Get-Service -Name 'postgresql*' -ErrorAction SilentlyContinue |
            Where-Object { $_.Status -eq 'Stopped' } |
            Select-Object -First 1
        if ($servicioPg) {
            try { Start-Service $servicioPg.Name } catch { }
        }
        for ($i = 0; $i -lt 20; $i++) {
            Start-Sleep -Milliseconds 500
            $pg = Get-NetTCPConnection -LocalPort 5432 -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
            if ($pg) { break }
        }
    }
    if (-not $pg) {
        throw "No hay nada escuchando en el puerto 5432. Inicie PostgreSQL y vuelva a ejecutar."
    }
    Escribir-Ok "PostgreSQL activo en el puerto 5432."

    # --- Frontend React/Vite en 5173 -------------------------------------
    $frontendExistente = $false
    $puerto5173 = Get-NetTCPConnection -LocalPort 5173 -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($puerto5173) {
        if (Probar-Url 'http://localhost:5173/') {
            Escribir-Ok "El frontend ya esta en ejecucion en http://localhost:5173."
            $frontendExistente = $true
        } else {
            throw "El puerto 5173 esta ocupado por un proceso que no responde como Vite. Cierrelo y reintente."
        }
    } else {
        Escribir-Info "Iniciando frontend React/Vite en http://localhost:5173..."
        $frontend = Iniciar-Servicio 'Frontend (Vite)' 'cmd.exe' @('/c', 'npm run dev -- --host 0.0.0.0 --port 5173') (Join-Path $raiz 'frontend') 'frontend-demo'
        $iniciados += $frontend
        if (-not (Esperar-Url 'http://localhost:5173/' 60 500)) {
            Mostrar-Log $frontend 30
            throw "El frontend no pudo iniciar a tiempo. Revise target/logs/frontend-demo.*.log"
        }
        Escribir-Ok "Frontend listo en http://localhost:5173."
    }

    # --- Tunel HTTPS hacia Vite (no hacia el backend) ---------------------
    $urlTunel = $null
    if (-not $SinTunel) {
        $cloudflared = Get-Command cloudflared -ErrorAction SilentlyContinue
        if ($cloudflared) {
            Escribir-Info "Abriendo tunel HTTPS hacia http://localhost:5173..."
            for ($intento = 1; $intento -le 3 -and -not $urlTunel; $intento++) {
                $tunel = Iniciar-Servicio 'Tunel Cloudflare' $cloudflared.Source @('tunnel', '--url', 'http://localhost:5173', '--no-autoupdate') $raiz 'cloudflared-demo'
                $iniciados += $tunel
                for ($i = 0; $i -lt 40 -and -not $urlTunel; $i++) {
                    Start-Sleep -Milliseconds 500
                    if (-not (Get-Process -Id $tunel.Id -ErrorAction SilentlyContinue)) { break }
                    $contenido = @()
                    foreach ($archivo in @($tunel.LogOut, $tunel.LogErr)) {
                        if (Test-Path $archivo) {
                            $contenido += Get-Content $archivo -Raw -ErrorAction SilentlyContinue
                        }
                    }
                    $textoTunel = $contenido -join "`n"
                    if ($textoTunel -match 'failed to request quick Tunnel|context deadline exceeded|failed to start tunnel') {
                        break
                    }
                    # La URL real del quick tunnel no es api.trycloudflare.com
                    # (esa aparece en mensajes de error de Cloudflare).
                    $coincidencia = [regex]::Match($textoTunel, 'https://(?!api\.)[a-z0-9][a-z0-9-]*\.trycloudflare\.com')
                    if ($coincidencia.Success) { $urlTunel = $coincidencia.Value }
                }
                if (-not $urlTunel) {
                    Detener-Servicio $tunel
                    if ($intento -lt 3) {
                        Escribir-Aviso "El intento $intento del tunel fallo; reintentando..."
                        Start-Sleep -Seconds 2
                    }
                }
            }
            if ($urlTunel) {
                $origenes = @($env:ALERTA_CORS_ORIGENES -split ',' |
                    ForEach-Object { $_.Trim() } |
                    Where-Object { $_ -and $_ -notmatch '^https://[a-z0-9-]+\.trycloudflare\.com$' })
                if ($origenes -notcontains $urlTunel) { $origenes += $urlTunel }
                $env:ALERTA_CORS_ORIGENES = ($origenes -join ',')
                Escribir-Ok "Tunel HTTPS: $urlTunel"
            } else {
                Escribir-Aviso "No se pudo abrir el tunel tras 3 intentos. Revise target/logs/cloudflared-demo.err.log"
            }
        } else {
            Escribir-Aviso "cloudflared no esta instalado. Sin tunel, las notificaciones solo funcionan en esta PC."
            Escribir-Aviso "Instalelo con: winget install --id Cloudflare.cloudflared"
        }
    }

    # --- Backend Spring Boot en 8081 -------------------------------------
    $backendExistente = $false
    $puerto8081 = Get-NetTCPConnection -LocalPort 8081 -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($puerto8081) {
        if (Probar-Url 'http://localhost:8081/api/push/config') {
            Escribir-Ok "El backend ya esta en ejecucion en http://localhost:8081."
            $backendExistente = $true
        } else {
            throw "El puerto 8081 esta ocupado por un proceso que no responde como el backend. Cierrelo y reintente."
        }
    } else {
        Escribir-Info "Iniciando backend Spring Boot en http://localhost:8081..."
        $backend = Iniciar-Servicio 'Backend (Spring Boot)' 'cmd.exe' @('/c', 'mvnw.cmd spring-boot:run') $raiz 'backend-demo'
        $iniciados += $backend
        if (-not (Esperar-Url 'http://localhost:8081/api/push/config' 90 1000)) {
            Mostrar-Log $backend 40
            throw "El backend no pudo iniciar a tiempo. Revise target/logs/backend-demo.*.log"
        }
        Escribir-Ok "Backend listo en http://localhost:8081."
    }

    # --- Verificacion frontend -> backend ---------------------------------
    Escribir-Info "Verificando el proxy de Vite hacia el backend..."
    try {
        $cfg = Invoke-RestMethod 'http://localhost:5173/api/push/config' -TimeoutSec 20
        if ($cfg.habilitado -eq $true) {
            Escribir-Ok "Proxy verificado; Firebase/FCM habilitado."
        } else {
            Escribir-Aviso "Proxy verificado, pero FCM no esta habilitado: $($cfg.motivo)"
        }
    } catch {
        throw "El frontend no puede comunicarse con el backend por /api. Detalle: $($_.Exception.Message)"
    }
    # El catalogo se carga despues de que Tomcat abre el puerto; se espera a
    # que esten los cuatro rios para no reportar una carga parcial.
    $rios = @()
    for ($i = 0; $i -lt 20; $i++) {
        try {
            # En Windows PowerShell 5.1, Invoke-RestMethod entrega el arreglo
            # JSON como un solo objeto; se asigna antes de envolverlo con @().
            $respuestaRios = Invoke-RestMethod 'http://localhost:5173/api/rios' -TimeoutSec 10
            $rios = @($respuestaRios)
            if ($rios.Count -ge 4) { break }
        } catch {
            # El backend o el proxy aun pueden estar terminando de arrancar.
        }
        Start-Sleep -Milliseconds 500
    }
    if ($rios.Count -gt 0) {
        Escribir-Ok "Catalogo de rios cargado: $($rios.Count) rios."
    } else {
        Escribir-Aviso "El catalogo de rios no respondio o llego vacio."
    }

    # --- Verificacion del tunel -------------------------------------------
    if ($urlTunel) {
        Escribir-Info "Verificando el tunel HTTPS (puede tardar unos segundos en propagar)..."
        $tunelOk = $false
        for ($i = 0; $i -lt 15; $i++) {
            try {
                $cfgTunel = Invoke-RestMethod "$urlTunel/api/push/config" -TimeoutSec 10
                if ($null -ne $cfgTunel) { $tunelOk = $true; break }
            } catch {
                Start-Sleep -Seconds 2
            }
        }
        if ($tunelOk) {
            Escribir-Ok "Tunel y DNS verificados."
        } else {
            Escribir-Aviso "El tunel aun esta propagando. Si el celular muestra error de DNS, espere y recargue."
        }
    }

    if (-not $SinNavegador) {
        Start-Process 'http://localhost:5173' | Out-Null
        Escribir-Ok "Navegador abierto en http://localhost:5173."
    }

    $adminId = if ($env:ALERTA_ADMIN_IDENTIFICADOR) { $env:ALERTA_ADMIN_IDENTIFICADOR } else { 'admin' }
    $adminPw = if ($env:ALERTA_ADMIN_PASSWORD) { $env:ALERTA_ADMIN_PASSWORD } else { '(defina ALERTA_ADMIN_PASSWORD en .env.demo)' }

    Write-Host ""
    Write-Host "======================================================================" -ForegroundColor Green
    Write-Host "   APLICACION LISTA PARA LA PRESENTACION" -ForegroundColor Green
    Write-Host "======================================================================" -ForegroundColor Green
    Write-Host ""
    Write-Host "ACCESOS:" -ForegroundColor Cyan
    Write-Host "  PC:      http://localhost:5173" -ForegroundColor White
    if ($urlTunel) {
        Write-Host "  Celular: $urlTunel" -ForegroundColor Magenta
    } else {
        Write-Host "  Celular: sin tunel; use la URL HTTPS de un tunel activo." -ForegroundColor Yellow
    }
    Write-Host ""
    Write-Host "CREDENCIALES:" -ForegroundColor Cyan
    Write-Host "  ADMIN (PC): usuario $adminId / clave $adminPw"
    Write-Host "  USER (celular): registre una cuenta distinta desde la pantalla de login."
    Write-Host ""
    Write-Host "FLUJO RECOMENDADO PARA LA DEMO:" -ForegroundColor Cyan
    Write-Host "  1. En el celular abra la URL HTTPS del tunel."
    Write-Host "  2. Registre una cuenta USER, siga un rio y pulse 'Activar notificaciones'."
    Write-Host "  3. En la PC inicie sesion como ADMIN, abra el mismo rio y pulse"
    Write-Host "     'Simular crecida y notificar'."
    Write-Host "  4. La notificacion llegara al celular con el texto real de alerta."
    Write-Host ""
    Write-Host "  Use cuentas distintas en PC y celular: el backend desactiva los"
    Write-Host "  tokens web anteriores del mismo usuario para evitar duplicados."
    Write-Host ""
    Write-Host "Presione Ctrl+C para detener los servicios iniciados por este lanzador." -ForegroundColor Yellow
    Write-Host ""

    while ($true) { Start-Sleep -Seconds 2 }
} catch {
    Escribir-Error $_.Exception.Message
    $codigoSalida = 1
} finally {
    if ($iniciados.Count -gt 0) {
        Write-Host ""
        Escribir-Aviso "Deteniendo los procesos iniciados por este lanzador..."
        for ($i = $iniciados.Count - 1; $i -ge 0; $i--) {
            Detener-Servicio $iniciados[$i]
        }
        Escribir-Ok "Procesos detenidos."
    }
}

exit $codigoSalida
