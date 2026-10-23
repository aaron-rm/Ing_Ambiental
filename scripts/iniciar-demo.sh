#!/usr/bin/env bash
# Arranca la aplicacion en Linux (equivalente a scripts/iniciar-demo.ps1).
# Las credenciales se leen de .env.demo (no versionado).
# Uso:  bash scripts/iniciar-demo.sh [--tunel]
set -euo pipefail

raiz="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
archivo_env="$raiz/.env.demo"

# Carga .env.demo tolerando finales de linea CRLF.
if [[ -f "$archivo_env" ]]; then
    while IFS= read -r linea || [[ -n "$linea" ]]; do
        linea="${linea%$'\r'}"
        [[ -z "${linea//[[:space:]]/}" || "${linea:0:1}" == "#" ]] && continue
        if [[ "$linea" == *=* ]]; then
            export "${linea%%=*}=${linea#*=}"
        fi
    done < "$archivo_env"
    echo "[OK] Variables cargadas desde .env.demo"
else
    echo "[AVISO] No existe .env.demo; se usaran las variables de esta sesion."
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

if [[ "${1:-}" == "--tunel" ]]; then
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
