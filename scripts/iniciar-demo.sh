#!/usr/bin/env bash
# Arranca la aplicacion en Linux (equivalente a scripts/iniciar-demo.ps1).
# - Con doble clic (sin terminal), se relanza dentro de Konsole/GNOME Terminal/xterm.
# - Abre el navegador en http://localhost:8081 cuando el backend este listo.
# - Las credenciales se leen de .env.demo (no versionado).
# Uso:  bash scripts/iniciar-demo.sh [--tunel] [--sin-navegador]
set -euo pipefail

script="$(readlink -f "${BASH_SOURCE[0]}")"
raiz="$(dirname "$(dirname "$script")")"
archivo_env="$raiz/.env.demo"

abrir_navegador() {
    if command -v xdg-open >/dev/null 2>&1 && [[ -n "${DISPLAY:-}${WAYLAND_DISPLAY:-}" ]]; then
        xdg-open "http://localhost:8081" >/dev/null 2>&1 || true
    fi
}

# Sin terminal interactiva (doble clic en el gestor de archivos): relanzar el
# script dentro de una terminal grafica para poder ver el menu de la consola.
if [[ ! -t 0 && "${ALERTA_DEMO_EN_TERMINAL:-}" != "1" ]]; then
    comando="cd $(printf '%q' "$raiz") && ALERTA_DEMO_EN_TERMINAL=1 bash $(printf '%q' "$script")"
    for arg in "$@"; do
        comando+=" $(printf '%q' "$arg")"
    done
    comando+='; echo; read -rp "Pulse Enter para cerrar esta ventana..."'
    if command -v konsole >/dev/null 2>&1; then
        exec konsole -e bash -c "$comando"
    elif command -v gnome-terminal >/dev/null 2>&1; then
        exec gnome-terminal -- bash -c "$comando"
    elif command -v xterm >/dev/null 2>&1; then
        exec xterm -e bash -c "$comando"
    else
        echo "No se encontro una terminal grafica (konsole, gnome-terminal o xterm)."
        echo "Ejecute manualmente en una terminal: bash $script"
        exit 1
    fi
fi

# Carga .env.demo tolerando finales de linea CRLF y valores con espacios.
if [[ -f "$archivo_env" ]]; then
    while IFS= read -r linea || [[ -n "$linea" ]]; do
        linea="${linea%$'\r'}"
        [[ -z "${linea//[[:space:]]/}" || "${linea:0:1}" == "#" ]] && continue
        if [[ "$linea" == *=* ]]; then
            clave="${linea%%=*}"
            valor="${linea#*=}"
            # Quita espacios alrededor de la clave y del valor.
            clave="${clave#"${clave%%[![:space:]]*}"}"
            clave="${clave%"${clave##*[![:space:]]}"}"
            valor="${valor#"${valor%%[![:space:]]*}"}"
            valor="${valor%"${valor##*[![:space:]]}"}"
            # Quita comillas dobles o simples que envuelvan todo el valor.
            if [[ ${#valor} -ge 2 && "$valor" == \"*\" ]]; then
                valor="${valor:1:${#valor}-2}"
            elif [[ ${#valor} -ge 2 && "$valor" == \'*\' ]]; then
                valor="${valor:1:${#valor}-2}"
            fi
            export "$clave=$valor"
        fi
    done < "$archivo_env"
    echo "[OK] Variables cargadas desde .env.demo"
else
    echo "[AVISO] No existe .env.demo; se usaran las variables de esta sesion."
fi

# La ruta de la credencial se interpreta relativa a la raiz del proyecto.
if [[ -n "${FIREBASE_CREDENTIALS_PATH:-}" && "${FIREBASE_CREDENTIALS_PATH}" != /* ]]; then
    FIREBASE_CREDENTIALS_PATH="$raiz/$FIREBASE_CREDENTIALS_PATH"
    export FIREBASE_CREDENTIALS_PATH
fi

if [[ -z "${ALERTA_ADMIN_PASSWORD:-}" ]]; then
    echo "[AVISO] ALERTA_ADMIN_PASSWORD vacio: no se creara la cuenta ADMIN y las"
    echo "        opciones 6, 7 y 8 de la consola responderan 403."
fi
if [[ "${FIREBASE_ENABLED:-true}" == "true" && ! -f "${FIREBASE_CREDENTIALS_PATH:-/no-existe}" ]]; then
    echo "[AVISO] FIREBASE_CREDENTIALS_PATH no apunta a un archivo existente;"
    echo "        FCM quedara deshabilitado. Para silenciar el aviso, use FIREBASE_ENABLED=false."
fi
if ! ss -ltn 2>/dev/null | grep -q ":5432 "; then
    echo "[AVISO] No hay nada escuchando en el puerto 5432 (PostgreSQL)."
    echo "        Levantelo con: podman start alerta-postgres"
fi

# Si el backend ya esta corriendo, no lanzar una segunda instancia.
if ss -ltn 2>/dev/null | grep -q ":8081 "; then
    echo "[INFO] El puerto 8081 ya esta en uso: parece que el backend ya se esta ejecutando."
    if [[ " $* " != *" --sin-navegador "* ]]; then
        abrir_navegador
    fi
    exit 0
fi

# Abrir el navegador en segundo plano en cuanto la aplicacion responda.
if [[ " $* " != *" --sin-navegador "* ]]; then
    (
        for _ in $(seq 1 90); do
            if curl -sf -o /dev/null http://localhost:8081/; then
                break
            fi
            sleep 2
        done
        abrir_navegador
    ) &
fi

if [[ " $* " == *" --tunel "* ]]; then
    if command -v cloudflared >/dev/null 2>&1; then
        cloudflared tunnel --url http://localhost:8081 &
        pid_tunel=$!
        trap 'kill "$pid_tunel" 2>/dev/null || true' EXIT
        echo "[OK] Tunel HTTPS iniciado; copie la URL https://...trycloudflare.com."
    else
        echo "[AVISO] cloudflared no esta instalado; sin tunel solo funciona localhost."
    fi
fi

cd "$raiz"
echo "[INFO] Iniciando el backend en http://localhost:8081 (Ctrl+C para detener)..."
bash mvnw spring-boot:run
