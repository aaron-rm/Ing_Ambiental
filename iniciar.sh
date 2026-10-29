#!/usr/bin/env bash
# ==============================================================================
# Lanzador Unificado - Sistema de Alerta Temprana de Inundaciones
# Inicia PostgreSQL (si hace falta), Backend, Frontend y Túnel HTTPS para Celular.
# ==============================================================================
set -euo pipefail

# Asegura que ~/.local/bin esté en el PATH (donde reside cloudflared)
export PATH="$HOME/.local/bin:$PATH"

script="$(readlink -f "${BASH_SOURCE[0]}")"
raiz="$(dirname "$script")"
archivo_env="$raiz/.env.demo"
dir_logs="$raiz/target/logs"
mkdir -p "$dir_logs"

# Códigos ANSI para colores
C_RESET="\033[0m"
C_BOLD="\033[1m"
C_VERDE="\033[32m"
C_CYAN="\033[36m"
C_AMARILLO="\033[33m"
C_ROJO="\033[31m"
C_MAGENTA="\033[35m"

log_info()  { echo -e "${C_CYAN}[INFO]${C_RESET} $*"; }
log_ok()    { echo -e "${C_VERDE}[✓]${C_RESET} $*"; }
log_aviso() { echo -e "${C_AMARILLO}[!]${C_RESET} $*"; }
log_error() { echo -e "${C_ROJO}[ERROR]${C_RESET} $*"; }

# Opciones por línea de comandos
USAR_TUNEL=true
ABRIR_NAVEGADOR=true

for arg in "$@"; do
    case "$arg" in
        --sin-tunel|--no-tunnel)
            USAR_TUNEL=false
            ;;
        --sin-navegador|--no-browser)
            ABRIR_NAVEGADOR=false
            ;;
        --ayuda|-h|--help)
            echo "Uso: ./iniciar.sh [OPCIONES]"
            echo ""
            echo "Opciones:"
            echo "  --sin-tunel      No iniciar el túnel HTTPS de Cloudflare (solo PC local)"
            echo "  --sin-navegador  No abrir el navegador automáticamente en la PC"
            echo "  --ayuda, -h      Muestra esta ayuda"
            exit 0
            ;;
    esac
done

# Sin terminal interactiva (doble clic en el gestor de archivos): relanzar en terminal gráfica
if [[ ! -t 0 && ! -t 1 && "${ALERTA_EN_TERMINAL:-}" != "1" && -n "${DISPLAY:-}${WAYLAND_DISPLAY:-}" ]]; then
    cmd="cd $(printf '%q' "$raiz") && ALERTA_EN_TERMINAL=1 bash $(printf '%q' "$script")"
    for arg in "$@"; do
        cmd+=" $(printf '%q' "$arg")"
    done
    cmd+='; echo; read -rp "Presione Enter para cerrar..."'
    if command -v konsole >/dev/null 2>&1; then
        exec konsole -e bash -c "$cmd"
    elif command -v gnome-terminal >/dev/null 2>&1; then
        exec gnome-terminal -- bash -c "$cmd"
    elif command -v xterm >/dev/null 2>&1; then
        exec xterm -e bash -c "$cmd"
    fi
fi

# Variables para control de procesos iniciados por este script
PID_BACKEND=""
PID_FRONTEND=""
PID_TUNEL=""
REUTILIZO_BACKEND=false
REUTILIZO_FRONTEND=false

detener_proceso() {
    local pid="$1"
    local nombre="$2"
    if [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null; then
        printf "  Deteniendo %s (PID %s)... " "$nombre" "$pid"
        pkill -P "$pid" 2>/dev/null || true
        kill "$pid" 2>/dev/null || true
        for _ in {1..8}; do
            kill -0 "$pid" 2>/dev/null || break
            sleep 0.25
        done
        if kill -0 "$pid" 2>/dev/null; then
            pkill -9 -P "$pid" 2>/dev/null || true
            kill -9 "$pid" 2>/dev/null || true
        fi
        echo -e "${C_VERDE}OK${C_RESET}"
    fi
}

limpiar() {
    trap - EXIT INT TERM
    echo ""
    echo -e "${C_AMARILLO}Finalizando procesos iniciados por este lanzador...${C_RESET}"
    detener_proceso "$PID_TUNEL" "Túnel Cloudflare"
    detener_proceso "$PID_FRONTEND" "Frontend (Vite)"
    detener_proceso "$PID_BACKEND" "Backend (Spring Boot)"
    echo -e "${C_VERDE}Sistema detenido limpiamente.${C_RESET}"
}
trap limpiar EXIT INT TERM

# ------------------------------------------------------------------------------
# 1. Cargar variables de entorno desde .env.demo
# ------------------------------------------------------------------------------
echo -e "${C_BOLD}==============================================================${C_RESET}"
echo -e "${C_BOLD} Sistema de Alerta Temprana de Inundaciones — Lanzador General${C_RESET}"
echo -e "${C_BOLD}==============================================================${C_RESET}"

if [[ -f "$archivo_env" ]]; then
    while IFS= read -r linea || [[ -n "$linea" ]]; do
        linea="${linea%$'\r'}"
        [[ -z "${linea//[[:space:]]/}" || "${linea:0:1}" == "#" ]] && continue
        if [[ "$linea" == *=* ]]; then
            clave="${linea%%=*}"
            valor="${linea#*=}"
            clave="${clave#"${clave%%[![:space:]]*}"}"
            clave="${clave%"${clave##*[![:space:]]}"}"
            valor="${valor#"${valor%%[![:space:]]*}"}"
            valor="${valor%"${valor##*[![:space:]]}"}"
            if [[ ${#valor} -ge 2 && "$valor" == \"*\" ]]; then
                valor="${valor:1:${#valor}-2}"
            elif [[ ${#valor} -ge 2 && "$valor" == \'*\' ]]; then
                valor="${valor:1:${#valor}-2}"
            fi
            export "$clave=$valor"
        fi
    done < "$archivo_env"
    log_ok "Variables de entorno cargadas desde .env.demo"
else
    log_aviso "No se encontró .env.demo; se utilizarán las variables del entorno actual."
fi

# Ajustar ruta relativa de Firebase al directorio raíz
if [[ -n "${FIREBASE_CREDENTIALS_PATH:-}" && "${FIREBASE_CREDENTIALS_PATH}" != /* ]]; then
    FIREBASE_CREDENTIALS_PATH="$raiz/$FIREBASE_CREDENTIALS_PATH"
    export FIREBASE_CREDENTIALS_PATH
fi

# Deshabilitar la consola interactiva en el backend para que corra como servicio limpio
export ALERTA_DEMO_CONSOLA_HABILITADA=false

# ------------------------------------------------------------------------------
# 2. Comprobar Dependencias y Servicios Base
# ------------------------------------------------------------------------------
if ! command -v java >/dev/null 2>&1; then
    log_error "Java no está instalado o no está en el PATH."
    exit 1
fi

if ! command -v node >/dev/null 2>&1 || ! command -v npm >/dev/null 2>&1; then
    log_error "Node.js o npm no están instalados."
    exit 1
fi

# Verificar PostgreSQL en el puerto 5432
if (echo > /dev/tcp/127.0.0.1/5432) >/dev/null 2>&1; then
    log_ok "PostgreSQL está activo y escuchando en el puerto 5432."
else
    log_aviso "PostgreSQL no responde en el puerto 5432. Intentando iniciar contenedor..."
    if command -v podman >/dev/null 2>&1 && podman container exists alerta-postgres 2>/dev/null; then
        podman start alerta-postgres >/dev/null 2>&1 || true
    elif command -v docker >/dev/null 2>&1 && docker ps -a --format '{{.Names}}' | grep -q "^alerta-postgres$"; then
        docker start alerta-postgres >/dev/null 2>&1 || true
    fi

    # Esperar hasta 10 segundos
    pg_ok=false
    for _ in {1..20}; do
        if (echo > /dev/tcp/127.0.0.1/5432) >/dev/null 2>&1; then
            pg_ok=true
            break
        fi
        sleep 0.5
    done

    if [[ "$pg_ok" == "true" ]]; then
        log_ok "Contenedor PostgreSQL (alerta-postgres) iniciado correctamente."
    else
        log_error "No se pudo conectar a PostgreSQL en localhost:5432."
        log_error "Asegúrese de iniciar su base de datos o ejecute: podman start alerta-postgres"
        exit 1
    fi
fi

# ------------------------------------------------------------------------------
# 3. Iniciar Backend (Spring Boot en puerto 8081)
# ------------------------------------------------------------------------------
if (echo > /dev/tcp/127.0.0.1/8081) >/dev/null 2>&1; then
    if curl -sf http://localhost:8081/api/push/config >/dev/null 2>&1; then
        log_ok "El backend ya se encuentra en ejecución en http://localhost:8081."
        REUTILIZO_BACKEND=true
    else
        log_error "El puerto 8081 está ocupado por otro proceso no compatible."
        exit 1
    fi
else
    log_info "Iniciando backend Spring Boot en http://localhost:8081..."
    (cd "$raiz" && bash mvnw spring-boot:run > "$dir_logs/backend.log" 2>&1) &
    PID_BACKEND=$!

    # Esperar a que el backend responda en /api/push/config (hasta 45 s)
    backend_listo=false
    printf "  Esperando backend"
    for _ in {1..45}; do
        if curl -sf http://localhost:8081/api/push/config >/dev/null 2>&1; then
            backend_listo=true
            break
        fi
        printf "."
        sleep 1
    done
    echo ""

    if [[ "$backend_listo" == "true" ]]; then
        log_ok "Backend iniciado y listo en http://localhost:8081."
    else
        log_error "El backend no pudo iniciar a tiempo. Revise el log: $dir_logs/backend.log"
        tail -n 25 "$dir_logs/backend.log"
        exit 1
    fi
fi

# ------------------------------------------------------------------------------
# 4. Iniciar Frontend (Vite en puerto 5173)
# ------------------------------------------------------------------------------
if (echo > /dev/tcp/127.0.0.1/5173) >/dev/null 2>&1; then
    if curl -sf http://localhost:5173/ >/dev/null 2>&1; then
        log_ok "El frontend ya se encuentra en ejecución en http://localhost:5173."
        REUTILIZO_FRONTEND=true
    else
        log_error "El puerto 5173 está ocupado por otro proceso."
        exit 1
    fi
else
    log_info "Iniciando frontend (React + Vite) en http://localhost:5173..."
    (cd "$raiz/frontend" && npx vite --host 0.0.0.0 --port 5173 > "$dir_logs/frontend.log" 2>&1) &
    PID_FRONTEND=$!

    frontend_listo=false
    printf "  Esperando frontend"
    for _ in {1..20}; do
        if curl -sf http://localhost:5173/ >/dev/null 2>&1; then
            frontend_listo=true
            break
        fi
        printf "."
        sleep 0.5
    done
    echo ""

    if [[ "$frontend_listo" == "true" ]]; then
        log_ok "Frontend iniciado y listo en http://localhost:5173."
    else
        log_error "El frontend no pudo iniciar a tiempo. Revise el log: $dir_logs/frontend.log"
        tail -n 20 "$dir_logs/frontend.log"
        exit 1
    fi
fi

# ------------------------------------------------------------------------------
# 5. Comprobar Comunicación Frontend <-> Backend
# ------------------------------------------------------------------------------
log_info "Verificando comunicación entre frontend y backend..."
cfg_resp=$(curl -s http://localhost:5173/api/push/config || true)
if [[ "$cfg_resp" == *"\"habilitado\":true"* ]]; then
    log_ok "Proxy frontend -> backend verificado (FCM habilitado y activo)."
elif [[ "$cfg_resp" == *"\"habilitado\":"* ]]; then
    log_aviso "Proxy verificado, pero Firebase reporta: $cfg_resp"
else
    log_error "El frontend no puede comunicarse con el backend vía proxy."
    log_error "Respuesta obtenida: $cfg_resp"
    exit 1
fi

rios_resp=$(curl -s http://localhost:5173/api/rios || true)
if [[ "$rios_resp" == *"Río Abajo"* ]]; then
    log_ok "Catálogo de ríos cargado correctamente a través del frontend."
else
    log_aviso "Catálogo no verificado a través del frontend. Respuesta: $rios_resp"
fi

# ------------------------------------------------------------------------------
# 6. Levantar Túnel HTTPS para Acceso Móvil
# ------------------------------------------------------------------------------
URL_TUNEL=""
if [[ "$USAR_TUNEL" == "true" ]]; then
    if ! command -v cloudflared >/dev/null 2>&1; then
        log_aviso "cloudflared no encontrado. Intentando usar ~/.local/bin/cloudflared..."
    fi

    if command -v cloudflared >/dev/null 2>&1; then
        log_info "Iniciando túnel Cloudflare HTTPS para acceso móvil..."
        cloudflared tunnel --url http://127.0.0.1:5173 > "$dir_logs/tunnel.log" 2>&1 &
        PID_TUNEL=$!

        printf "  Obteniendo URL pública HTTPS"
        for _ in {1..20}; do
            URL_TUNEL=$(grep -o "https://[a-zA-Z0-9-]\+\.trycloudflare\.com" "$dir_logs/tunnel.log" | head -n 1 || true)
            [[ -n "$URL_TUNEL" ]] && break
            printf "."
            sleep 1
        done
        echo ""

        if [[ -n "$URL_TUNEL" ]]; then
            log_ok "Túnel HTTPS establecido: $URL_TUNEL"
            printf "  Verificando propagación de DNS en Cloudflare"
            dns_ok=false
            for _ in {1..15}; do
                if curl -sf -m 5 "$URL_TUNEL/api/push/config" >/dev/null 2>&1; then
                    dns_ok=true
                    break
                fi
                printf "."
                sleep 1.5
            done
            echo ""
            if [[ "$dns_ok" == "true" ]]; then
                log_ok "Conexión externa y DNS verificados con éxito."
            else
                log_aviso "El túnel está propagando en la red; si el navegador muestra DNS_PROBE, espere unos segundos y recargue."
            fi
        else
            log_aviso "No se pudo extraer la URL del túnel. Revise: $dir_logs/tunnel.log"
        fi
    else
        log_aviso "cloudflared no está disponible. Para acceso desde el celular instálelo o use la IP local (sin push)."
    fi
fi

# ------------------------------------------------------------------------------
# 7. Abrir Navegador Local
# ------------------------------------------------------------------------------
if [[ "$ABRIR_NAVEGADOR" == "true" ]]; then
    if command -v xdg-open >/dev/null 2>&1 && [[ -n "${DISPLAY:-}${WAYLAND_DISPLAY:-}" ]]; then
        xdg-open "http://localhost:5173" >/dev/null 2>&1 &
        log_ok "Navegador local abierto en http://localhost:5173"
    fi
fi

# ------------------------------------------------------------------------------
# 8. Panel de Información para la Presentación
# ------------------------------------------------------------------------------
admin_id="${ALERTA_ADMIN_IDENTIFICADOR:-admin}"
admin_pw="${ALERTA_ADMIN_PASSWORD:-admin-local-clave}"

echo ""
echo -e "${C_BOLD}${C_VERDE}======================================================================${C_RESET}"
echo -e "${C_BOLD}${C_VERDE}   APLICACIÓN LISTA Y OPERATIVA PARA LA PRESENTACIÓN                  ${C_RESET}"
echo -e "${C_BOLD}${C_VERDE}======================================================================${C_RESET}"
echo ""
echo -e "${C_BOLD}ACCESOS:${C_RESET}"
echo -e "  💻  En este ordenador:    ${C_CYAN}${C_BOLD}http://localhost:5173${C_RESET}"
if [[ -n "$URL_TUNEL" ]]; then
    echo -e "  📱  En el celular (HTTPS): ${C_MAGENTA}${C_BOLD}$URL_TUNEL${C_RESET}"
else
    echo -e "  📱  En el celular:         (Túnel no activo; use --tunel o verifique cloudflared)"
fi
echo ""
echo -e "${C_BOLD}CREDENCIALES DE PRESENTACIÓN:${C_RESET}"
echo -e "  • Rol ADMIN (Simulaciones): Usuario: ${C_CYAN}$admin_id${C_RESET} | Clave: ${C_CYAN}$admin_pw${C_RESET}"
echo -e "  • Rol USER  (Monitoreo):    Puedes registrarte libremente desde la pantalla de login"
echo ""
echo -e "${C_BOLD}INSTRUCCIONES PARA PROBAR NOTIFICACIONES EN EL CELULAR:${C_RESET}"
echo -e "  1. En el teléfono, abre la URL HTTPS de arriba en el navegador:"
echo -e "     • ${C_BOLD}Android:${C_RESET} Abre en ${C_BOLD}Google Chrome${C_RESET}. Inicia sesión con cualquier cuenta."
echo -e "       (Si usas Brave, activa 'Usar servicios Google para push' en Ajustes → Privacidad)."
echo -e "     • ${C_BOLD}iPhone / iPad (iOS 16.4+):${C_RESET} Abre en ${C_BOLD}Safari${C_RESET}, toca el botón Compartir (⎋),"
echo -e "       selecciona ${C_BOLD}'Agregar a la pantalla de inicio'${C_RESET} y abre la app desde el icono instalado."
echo -e "  2. Toca un río (o ve a 'Mis ríos'), suscríbete y pulsa ${C_BOLD}'Activar notificaciones'${C_RESET}."
echo -e "     Acepta el diálogo de permisos del sistema."
echo -e "  3. Para probar la alerta: inicia sesión con la cuenta ${C_BOLD}ADMIN${C_RESET} en la PC o celular,"
echo -e "     abre el río que estás siguiendo y pulsa ${C_BOLD}'Simular crecida y notificar'${C_RESET}."
echo -e "     ¡La notificación push llegará al instante con sonido y vibración!"
echo ""
echo -e "${C_AMARILLO}Presione Ctrl + C para detener todos los servicios de forma limpia.${C_RESET}"
echo ""

# Mantener en ejecución atendiendo señales de terminación
while true; do
    sleep 2 &
    wait $! 2>/dev/null || break
done
