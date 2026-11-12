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

# Autorizar el patrón de los túneles temporales de Cloudflare. El frontend React
# usa el proxy de Vite, pero esto también cubre accesos directos al backend.
if ! printf '%s' ",${ALERTA_CORS_ORIGENES:-}," | grep -qF ',https://*.trycloudflare.com,'; then
    ALERTA_CORS_ORIGENES="${ALERTA_CORS_ORIGENES:-http://localhost:3000,http://localhost:5173,http://localhost:8081,http://127.0.0.1:8081},https://*.trycloudflare.com"
    export ALERTA_CORS_ORIGENES
fi

if [[ -z "${DB_PASSWORD:-}" ]]; then
    log_aviso "DB_PASSWORD está vacío; el backend no podrá conectar con PostgreSQL."
fi
if [[ "${FIREBASE_ENABLED:-true}" != "false" ]]; then
    if [[ -z "${FIREBASE_CREDENTIALS_PATH:-}" || ! -f "${FIREBASE_CREDENTIALS_PATH}" ]]; then
        log_aviso "FIREBASE_CREDENTIALS_PATH no existe en esta máquina; FCM quedará deshabilitado."
    fi
fi

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

# Vite 8 requiere Node moderno. Se avisa, pero se deja que Vite dé el error
# definitivo si la versión no es compatible.
node_version="$(node -v 2>/dev/null || true)"
log_info "Node.js detectado: ${node_version:-desconocido}"

# Las dependencias del frontend no se versionan. En una laptop recién clonada
# hay que instalarlas antes de arrancar Vite. Si la carpeta se sincronizó desde
# Windows (por ejemplo, con OneDrive), los binarios nativos de Rolldown no
# sirven en Linux y Vite falla al arrancar: se reinstalan para esta plataforma.
reinstalar_frontend=false
if [[ ! -x "$raiz/frontend/node_modules/.bin/vite" || ! -d "$raiz/frontend/node_modules/react" ]]; then
    reinstalar_frontend=true
elif [[ "$(uname -s)" == "Linux" ]] && ! compgen -G "$raiz/frontend/node_modules/@rolldown/binding-linux-*" >/dev/null; then
    reinstalar_frontend=true
    log_aviso "Las dependencias del frontend vienen de otra plataforma; se reinstalarán para Linux."
fi

if [[ "$reinstalar_frontend" == "true" ]]; then
    log_info "Instalando dependencias del frontend (npm install)..."
    if ! (cd "$raiz/frontend" && npm install); then
        log_error "No se pudieron instalar las dependencias del frontend."
        log_error "Revise frontend/package.json y la conexión a Internet."
        exit 1
    fi
    log_ok "Dependencias del frontend instaladas."
else
    log_ok "Dependencias del frontend presentes."
fi

# Si target/ se sincronizó desde Windows con copias *-safeBackup-*, Maven puede
# fallar con "Unable to find a single main class". Un clean lo resuelve.
if find "$raiz/target/classes" -name '*safeBackup*' -print -quit 2>/dev/null | grep -q .; then
    log_aviso "Se detectaron clases duplicadas de una sincronización anterior; limpiando target/..."
    if ! (cd "$raiz" && bash mvnw -q clean); then
        log_error "No se pudo limpiar target/. Ejecute: bash mvnw clean"
        exit 1
    fi
    mkdir -p "$dir_logs"
    log_ok "target/ limpio."
fi

# Verificar PostgreSQL: puerto, contenedor de la demo y credenciales reales.
# Extraer host, puerto y base de DB_URL (jdbc:postgresql://host:puerto/base).
db_host="localhost"
db_port="5432"
db_name="alerta_inundaciones"
if [[ "${DB_URL:-}" =~ ^jdbc:postgresql://([^/:?]+)(:([0-9]+))?/([^?]+) ]]; then
    db_host="${BASH_REMATCH[1]}"
    db_port="${BASH_REMATCH[3]:-5432}"
    db_name="${BASH_REMATCH[4]}"
fi

# Detectar el contenedor de la demo (Podman o Docker).
pg_contenedor=""
pg_contenedor_activo=false
if command -v podman >/dev/null 2>&1 && podman container exists alerta-postgres 2>/dev/null; then
    pg_contenedor="podman"
    if podman ps --format '{{.Names}}' 2>/dev/null | grep -qx 'alerta-postgres'; then
        pg_contenedor_activo=true
    fi
elif command -v docker >/dev/null 2>&1 && docker ps -a --format '{{.Names}}' 2>/dev/null | grep -qx 'alerta-postgres'; then
    pg_contenedor="docker"
    if docker ps --format '{{.Names}}' 2>/dev/null | grep -qx 'alerta-postgres'; then
        pg_contenedor_activo=true
    fi
fi

puerto_pg_abierto=false
if (echo > /dev/tcp/127.0.0.1/"$db_port") >/dev/null 2>&1; then
    puerto_pg_abierto=true
fi

# Si el contenedor de la demo está detenido y el puerto lo ocupa otro
# PostgreSQL (por ejemplo, el servicio nativo de Fedora), no se puede arrancar.
if [[ -n "$pg_contenedor" && "$pg_contenedor_activo" == "false" && "$puerto_pg_abierto" == "true" ]]; then
    log_error "El contenedor alerta-postgres está detenido y el puerto $db_port ya está ocupado."
    if systemctl list-units --type=service --state=active --no-legend 2>/dev/null | grep -qE 'postgresql[^ ]*\.service'; then
        log_error "El puerto lo ocupa PostgreSQL del sistema. Para usar los datos de la demo:"
        log_error "  sudo systemctl stop postgresql"
        log_error "  $pg_contenedor start alerta-postgres"
    else
        log_error "Detenga el proceso que usa el puerto $db_port o use el contenedor de la demo."
    fi
    exit 1
fi

# Si el contenedor existe y está detenido, arrancarlo.
if [[ -n "$pg_contenedor" && "$pg_contenedor_activo" == "false" && "$puerto_pg_abierto" == "false" ]]; then
    log_info "Iniciando contenedor PostgreSQL (alerta-postgres)..."
    if $pg_contenedor start alerta-postgres >/dev/null 2>&1; then
        for _ in {1..20}; do
            if (echo > /dev/tcp/127.0.0.1/"$db_port") >/dev/null 2>&1; then
                puerto_pg_abierto=true
                pg_contenedor_activo=true
                break
            fi
            sleep 0.5
        done
    fi
fi

if [[ "$puerto_pg_abierto" == "true" ]]; then
    log_ok "PostgreSQL está activo y escuchando en el puerto $db_port."
else
    log_aviso "PostgreSQL no responde en el puerto $db_port."
    if [[ -n "$pg_contenedor" ]]; then
        $pg_contenedor start alerta-postgres >/dev/null 2>&1 || true
    else
        log_aviso "Si PostgreSQL es un servicio del sistema, ejecute: sudo systemctl start postgresql"
    fi
    for _ in {1..20}; do
        if (echo > /dev/tcp/127.0.0.1/"$db_port") >/dev/null 2>&1; then
            puerto_pg_abierto=true
            break
        fi
        sleep 0.5
    done
    if [[ "$puerto_pg_abierto" == "true" ]]; then
        log_ok "PostgreSQL iniciado en el puerto $db_port."
    else
        log_error "No se pudo conectar a PostgreSQL en $db_host:$db_port."
        log_error "Inicie la base de datos o ejecute: podman start alerta-postgres"
        exit 1
    fi
fi

# Validar las credenciales antes de arrancar Maven. Sin esto, una
# contraseña incorrecta tarda 90 s en aparecer como un error de Hibernate.
pg_pw_contenedor=""
if [[ "$pg_contenedor_activo" == "true" ]]; then
    if [[ "$pg_contenedor" == "podman" ]]; then
        pg_pw_contenedor="$(podman exec alerta-postgres printenv POSTGRES_PASSWORD 2>/dev/null || true)"
    else
        pg_pw_contenedor="$(docker exec alerta-postgres printenv POSTGRES_PASSWORD 2>/dev/null || true)"
    fi
    # Verificar si la base de datos existe en el contenedor; si no, crearla
    if ! $pg_contenedor exec alerta-postgres psql -U postgres -lqt 2>/dev/null | cut -d \| -f 1 | grep -qw "$db_name"; then
        log_aviso "La base '$db_name' no existe en el contenedor; creándola..."
        $pg_contenedor exec alerta-postgres createdb -U postgres "$db_name" >/dev/null 2>&1 || true
    fi
fi

pg_error="$(mktemp)"
pg_ok=false

probar_conexion_pg() {
    local h="$1"
    local p="$2"
    local u="$3"
    local pass="$4"
    local db="$5"

    if command -v psql >/dev/null 2>&1; then
        PGCONNECT_TIMEOUT=5 PGPASSWORD="$pass" psql -w -h "$h" -p "$p" -U "$u" -d "$db" -c 'select 1;' >/dev/null 2>"$pg_error"
        return $?
    elif [[ "$pg_contenedor_activo" == "true" ]]; then
        $pg_contenedor exec -e PGPASSWORD="$pass" alerta-postgres psql -w -h "$h" -p "$p" -U "$u" -d "$db" -c 'select 1;' >/dev/null 2>"$pg_error"
        return $?
    else
        return 127
    fi
}

# 1. Probar con las variables actuales de .env.demo
if probar_conexion_pg "$db_host" "$db_port" "${DB_USERNAME:-postgres}" "${DB_PASSWORD:-}" "$db_name"; then
    pg_ok=true
# 2. Si localhost falla por resolución IPv6 (::1), probar con 127.0.0.1
elif [[ "$db_host" == "localhost" ]] \
    && probar_conexion_pg "127.0.0.1" "$db_port" "${DB_USERNAME:-postgres}" "${DB_PASSWORD:-}" "$db_name"; then
    if [[ -n "${DB_URL:-}" ]]; then
        DB_URL="${DB_URL/localhost/127.0.0.1}"
        export DB_URL
    fi
    db_host="127.0.0.1"
    pg_ok=true
    log_aviso "localhost no conectaba; se usará 127.0.0.1 para la base de datos."
# 3. Si hay contenedor y la contraseña de .env.demo difiere, probar con la contraseña del contenedor
elif [[ "$pg_contenedor_activo" == "true" && -n "$pg_pw_contenedor" ]] \
    && probar_conexion_pg "$db_host" "$db_port" "${DB_USERNAME:-postgres}" "$pg_pw_contenedor" "$db_name"; then
    DB_PASSWORD="$pg_pw_contenedor"
    export DB_PASSWORD
    pg_ok=true
    log_aviso "La contraseña de .env.demo no coincidía con el contenedor; se usará la del contenedor."
# 4. Combinación: 127.0.0.1 + contraseña del contenedor
elif [[ "$pg_contenedor_activo" == "true" && -n "$pg_pw_contenedor" && "$db_host" == "localhost" ]] \
    && probar_conexion_pg "127.0.0.1" "$db_port" "${DB_USERNAME:-postgres}" "$pg_pw_contenedor" "$db_name"; then
    if [[ -n "${DB_URL:-}" ]]; then
        DB_URL="${DB_URL/localhost/127.0.0.1}"
        export DB_URL
    fi
    db_host="127.0.0.1"
    DB_PASSWORD="$pg_pw_contenedor"
    export DB_PASSWORD
    pg_ok=true
    log_aviso "Se usará 127.0.0.1 y la contraseña del contenedor alerta-postgres."
fi

if [[ "$pg_ok" == "true" ]]; then
    log_ok "Conexión a PostgreSQL verificada (base $db_name)."
    rm -f "$pg_error"
elif [[ "$pg_contenedor_activo" != "true" ]] && ! command -v psql >/dev/null 2>&1; then
    # Ni psql en host ni contenedor activo
    log_aviso "psql no está instalado en el host ni hay un contenedor activo; no se pudieron validar las credenciales antes de arrancar."
    log_aviso "Si falla la conexión, instale el cliente con: sudo dnf install postgresql"
    rm -f "$pg_error"
else
    pg_detalle="$(tr '\n' ' ' < "$pg_error" | cut -c1-300)"
    log_error "PostgreSQL responde, pero la conexión falló."
    log_error "Detalle: $pg_detalle"
    if grep -qiE 'password authentication failed|no password supplied' "$pg_error"; then
        log_error "La contraseña de DB_PASSWORD no es válida para el usuario ${DB_USERNAME:-postgres}."
        if [[ "$pg_contenedor_activo" == "true" ]]; then
            log_error "Actualice DB_PASSWORD en .env.demo con la contraseña del contenedor."
            log_error "Puede consultarla con: $pg_contenedor exec alerta-postgres printenv POSTGRES_PASSWORD"
        else
            log_error "Si usa PostgreSQL del sistema, restablezca la clave:"
            log_error "  sudo -u postgres psql -c \"ALTER USER postgres PASSWORD 'la-clave-de-.env.demo';\""
        fi
    elif grep -qiE 'database .* does not exist' "$pg_error"; then
        log_error "La base '$db_name' no existe."
        if [[ "$pg_contenedor_activo" == "true" ]]; then
            log_error "Créela con: $pg_contenedor exec alerta-postgres createdb -U postgres $db_name"
        else
            log_error "Créela con: sudo -u postgres createdb $db_name"
        fi
    elif grep -qiE 'Connection refused|could not connect' "$pg_error"; then
        log_error "No se pudo conectar a $db_host:$db_port."
    elif grep -qiE 'no pg_hba.conf entry' "$pg_error"; then
        log_error "PostgreSQL rechazó la conexión por pg_hba.conf."
    fi
    log_error "Diagnóstico ampliado: bash scripts/diagnosticar-postgres.sh"
    rm -f "$pg_error"
    exit 1
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

    # Esperar a que el backend responda en /api/push/config (hasta 90 s;
    # una laptop con Maven en frío puede tardar más que un equipo ya compilado).
    backend_listo=false
    printf "  Esperando backend"
    for _ in {1..90}; do
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
        if [[ -f "$dir_logs/backend.log" ]]; then
            log_error "Causa probable:"
            causas=$(grep -aE 'Caused by|FATAL|password authentication|does not exist|Connection refused|no pg_hba|HikariPool|Unable to determine Dialect' "$dir_logs/backend.log" | tail -n 12 || true)
            if [[ -n "$causas" ]]; then
                while IFS= read -r linea; do log_error "  $linea"; done <<< "$causas"
            else
                tail -n 10 "$dir_logs/backend.log" | while IFS= read -r linea; do log_error "  $linea"; done
            fi
        fi
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
    (cd "$raiz/frontend" && npm run dev -- --host 0.0.0.0 --port 5173 --strictPort > "$dir_logs/frontend.log" 2>&1) &
    PID_FRONTEND=$!

    frontend_listo=false
    printf "  Esperando frontend"
    for _ in {1..40}; do
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

rios_ok=false
rios_resp=""
for _ in {1..20}; do
    rios_resp=$(curl -s http://localhost:5173/api/rios || true)
    if [[ "$rios_resp" == *"Río Abajo"* ]]; then
        rios_ok=true
        break
    fi
    sleep 0.5
done
if [[ "$rios_ok" == "true" ]]; then
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
        for intento in 1 2 3; do
            URL_TUNEL=""
            : > "$dir_logs/tunnel.log"
            cloudflared tunnel --url http://127.0.0.1:5173 --no-autoupdate > "$dir_logs/tunnel.log" 2>&1 &
            PID_TUNEL=$!

            printf "  Obteniendo URL pública HTTPS"
            for _ in {1..25}; do
                # La URL real del quick tunnel tiene guiones; api.trycloudflare.com
                # aparece en los mensajes de error de Cloudflare y no sirve.
                URL_TUNEL=$(grep -Eo 'https://[a-zA-Z0-9]+(-[a-zA-Z0-9]+)+\.trycloudflare\.com' "$dir_logs/tunnel.log" | head -n 1 || true)
                [[ -n "$URL_TUNEL" ]] && break
                if grep -q 'failed to request quick Tunnel\|context deadline exceeded' "$dir_logs/tunnel.log"; then
                    break
                fi
                if ! kill -0 "$PID_TUNEL" 2>/dev/null; then
                    break
                fi
                printf "."
                sleep 1
            done
            echo ""

            if [[ -n "$URL_TUNEL" ]]; then
                break
            fi

            log_aviso "El intento $intento del túnel falló; reintentando..."
            detener_proceso "$PID_TUNEL" "Túnel Cloudflare"
            PID_TUNEL=""
            sleep 2
        done

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
            log_aviso "No se pudo abrir el túnel tras 3 intentos. Revise: $dir_logs/tunnel.log"
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
    echo -e "  📱  En el celular:         (Túnel no activo; verifique cloudflared o use --sin-tunel solo para PC)"
fi
echo ""
echo -e "${C_BOLD}CREDENCIALES DE PRESENTACIÓN:${C_RESET}"
echo -e "  • Rol ADMIN (Simulaciones): Usuario: ${C_CYAN}$admin_id${C_RESET} | Clave: ${C_CYAN}$admin_pw${C_RESET}"
echo -e "  • Rol USER  (Monitoreo):    Puedes registrarte libremente desde la pantalla de login"
echo ""
echo -e "${C_BOLD}INSTRUCCIONES PARA PROBAR NOTIFICACIONES EN EL CELULAR:${C_RESET}"
echo -e "  1. En el teléfono, abre la URL HTTPS de arriba en el navegador:"
echo -e "     • ${C_BOLD}Android:${C_RESET} Abre en ${C_BOLD}Google Chrome${C_RESET}. Registra una cuenta USER distinta a la del ADMIN."
echo -e "       (Si usas Brave, activa 'Usar servicios Google para push' en Ajustes → Privacidad)."
echo -e "     • ${C_BOLD}iPhone / iPad (iOS 16.4+):${C_RESET} Abre en ${C_BOLD}Safari${C_RESET}, toca el botón Compartir (⎋),"
echo -e "       selecciona ${C_BOLD}'Agregar a la pantalla de inicio'${C_RESET} y abre la app desde el icono instalado."
echo -e "  2. Toca un río (o ve a 'Mis ríos'), suscríbete y pulsa ${C_BOLD}'Activar notificaciones'${C_RESET}."
echo -e "     Acepta el diálogo de permisos del sistema."
echo -e "  3. En la PC inicia sesión como ${C_BOLD}ADMIN${C_RESET}, abre el mismo río y pulsa"
echo -e "     ${C_BOLD}'Simular crecida y notificar'${C_RESET}. ¡La notificación llegará al celular!"
echo -e "     Usa cuentas distintas en PC y celular: el backend desactiva los tokens web"
echo -e "     anteriores del mismo usuario para evitar notificaciones duplicadas."
echo -e "  4. Si el celular ya había abierto un túnel anterior, abre la URL nueva y"
echo -e "     vuelve a pulsar 'Activar notificaciones' (cada túnel es un origen distinto)."
echo ""
echo -e "${C_AMARILLO}Presione Ctrl + C para detener todos los servicios de forma limpia.${C_RESET}"
echo ""

# Mantener en ejecución atendiendo señales de terminación
while true; do
    sleep 2 &
    wait $! 2>/dev/null || break
done
