#!/usr/bin/env bash
# Diagnóstico de PostgreSQL para la demo en Fedora/Linux.
# No imprime la contraseña; solo indica si está definida y su longitud.
# Uso: bash scripts/diagnosticar-postgres.sh
set -uo pipefail

script="$(readlink -f "${BASH_SOURCE[0]}")"
raiz="$(dirname "$(dirname "$script")")"
archivo_env="$raiz/.env.demo"

# Cargar .env.demo con la misma lógica que iniciar.sh.
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
else
    echo "No existe $archivo_env"
fi

db_host="localhost"
db_port="5432"
db_name="alerta_inundaciones"
if [[ "${DB_URL:-}" =~ ^jdbc:postgresql://([^/:?]+)(:([0-9]+))?/([^?]+) ]]; then
    db_host="${BASH_REMATCH[1]}"
    db_port="${BASH_REMATCH[3]:-5432}"
    db_name="${BASH_REMATCH[4]}"
fi

echo "== Configuración de .env.demo =="
echo "DB_URL=${DB_URL:-<sin definir>}"
echo "DB_USERNAME=${DB_USERNAME:-postgres}"
if [[ -n "${DB_PASSWORD:-}" ]]; then
    echo "DB_PASSWORD=definida (${#DB_PASSWORD} caracteres)"
else
    echo "DB_PASSWORD=VACÍA"
fi
echo "Destino: $db_host:$db_port/$db_name"
echo

echo "== Puerto $db_port =="
if (echo > /dev/tcp/127.0.0.1/"$db_port") >/dev/null 2>&1; then
    echo "Escuchando en 127.0.0.1:$db_port"
else
    echo "NADA escuchando en 127.0.0.1:$db_port"
fi
if command -v ss >/dev/null 2>&1; then
    ss -ltnp 2>/dev/null | grep ":$db_port" || true
fi
echo

echo "== Servicios PostgreSQL del sistema =="
if command -v systemctl >/dev/null 2>&1; then
    servicios=$(systemctl list-units --type=service --state=active --no-legend 2>/dev/null | grep -i postgres || true)
    if [[ -n "$servicios" ]]; then
        echo "$servicios"
    else
        echo "Ninguno activo."
    fi
else
    echo "systemctl no disponible."
fi
echo

echo "== Contenedores de la demo =="
encontrado=false
pg_contenedor=""
pg_contenedor_activo=false
if command -v podman >/dev/null 2>&1; then
    salida_podman=$(podman ps -a --filter name=alerta-postgres --format 'podman: {{.Names}} | {{.Status}} | {{.Ports}}' 2>/dev/null || true)
    if [[ -n "$salida_podman" ]]; then
        echo "$salida_podman"
        encontrado=true
        pg_contenedor="podman"
        if podman ps --format '{{.Names}}' 2>/dev/null | grep -qx 'alerta-postgres'; then
            pg_contenedor_activo=true
        fi
    fi
fi
if command -v docker >/dev/null 2>&1; then
    salida_docker=$(docker ps -a --filter name=alerta-postgres --format 'docker: {{.Names}} | {{.Status}} | {{.Ports}}' 2>/dev/null || true)
    if [[ -n "$salida_docker" ]]; then
        echo "$salida_docker"
        encontrado=true
        pg_contenedor="docker"
        if docker ps --format '{{.Names}}' 2>/dev/null | grep -qx 'alerta-postgres'; then
            pg_contenedor_activo=true
        fi
    fi
fi
if [[ "$encontrado" == "false" ]]; then
    echo "No se encontró el contenedor alerta-postgres."
fi

pw_contenedor=""
if [[ "$pg_contenedor_activo" == "true" ]]; then
    pw_contenedor="$($pg_contenedor exec alerta-postgres printenv POSTGRES_PASSWORD 2>/dev/null || true)"
    if [[ -n "$pw_contenedor" ]]; then
        echo "Contraseña del contenedor: definida (${#pw_contenedor} caracteres)"
        if [[ -n "${DB_PASSWORD:-}" && "$DB_PASSWORD" != "$pw_contenedor" ]]; then
            echo "AVISO: DB_PASSWORD en .env.demo (${#DB_PASSWORD} caracteres) NO coincide con la del contenedor (${#pw_contenedor} caracteres)."
            echo "       OneDrive suele sincronizar la contraseña de Windows; iniciar.sh usará la del contenedor automáticamente."
        fi
    fi
    # Verificar si la base de datos existe en el contenedor
    if $pg_contenedor exec alerta-postgres psql -U postgres -lqt 2>/dev/null | cut -d \| -f 1 | grep -qw "$db_name"; then
        echo "Base '$db_name' en contenedor: EXISTE"
    else
        echo "Base '$db_name' en contenedor: NO EXISTE (ejecute: $pg_contenedor exec alerta-postgres createdb -U postgres $db_name)"
    fi
fi
echo

echo "== Cliente psql =="
if command -v psql >/dev/null 2>&1; then
    psql --version
else
    echo "psql en host: NO instalado (instalar con: sudo dnf install postgresql)"
    if [[ "$pg_contenedor_activo" == "true" ]]; then
        echo "psql en contenedor alerta-postgres: DISPONIBLE"
    fi
fi
echo

echo "== Prueba de conexión TCP (127.0.0.1) =="
echo "NOTA: se fuerza -h 127.0.0.1 y PGPASSFILE=/dev/null para simular cómo"
echo "      conecta Spring Boot (JDBC/TCP, nunca socket Unix)."
ejecutar_prueba() {
    local h="$1"
    local pass="$2"
    local db="$3"

    if command -v psql >/dev/null 2>&1; then
        PGPASSFILE=/dev/null PGCONNECT_TIMEOUT=5 PGPASSWORD="$pass" \
            psql -w -h "$h" -p "$db_port" -U "${DB_USERNAME:-postgres}" -d "$db" -c 'select 1;' 2>&1
    elif [[ "$pg_contenedor_activo" == "true" ]]; then
        $pg_contenedor exec -e "PGPASSWORD=$pass" alerta-postgres \
            psql -w -h 127.0.0.1 -p 5432 -U "${DB_USERNAME:-postgres}" -d "$db" -c 'select 1;' 2>&1
    else
        echo "NO_PSQL"
        return 127
    fi
}

pg_host_tcp="${db_host}"
[[ "$pg_host_tcp" == "localhost" ]] && pg_host_tcp="127.0.0.1"

salida="$(ejecutar_prueba "$pg_host_tcp" "${DB_PASSWORD:-}" "$db_name")"
codigo=$?

if [[ "$salida" == "NO_PSQL" ]]; then
    echo "No se puede probar la conexión: psql no está instalado en el host y no hay contenedor activo."
    echo "Instale el cliente con: sudo dnf install postgresql"
else
    echo "Destino: $pg_host_tcp:$db_port / base: $db_name / usuario: ${DB_USERNAME:-postgres}"
    echo "$salida"
    echo "Código de salida: $codigo"
    if [[ $codigo -eq 0 ]]; then
        echo "Conexión TCP correcta con las credenciales de .env.demo."
    else
        # Si falló, intentar con la clave del contenedor
        if [[ "$pg_contenedor_activo" == "true" && -n "$pw_contenedor" && "$pw_contenedor" != "${DB_PASSWORD:-}" ]]; then
            echo
            echo "Probando con la contraseña del contenedor alerta-postgres..."
            salida_cont="$(ejecutar_prueba "$pg_host_tcp" "$pw_contenedor" "$db_name")"
            if [[ $? -eq 0 ]]; then
                echo "Contenedor: CONEXIÓN TCP EXITOSA con la clave del contenedor. iniciar.sh la usará automáticamente."
            fi
        fi

        echo
        echo "== Sugerencias =="
        case "$salida" in
            *"password authentication failed"*|*"no password supplied"*)
                echo "- DB_PASSWORD en .env.demo no coincide con la contraseña TCP de PostgreSQL."
                if [[ "$pg_contenedor_activo" == "true" ]]; then
                    echo "- Contenedor alerta-postgres activo. La clave se obtiene con:"
                    echo "    $pg_contenedor exec alerta-postgres printenv POSTGRES_PASSWORD"
                    echo "  iniciar.sh la detecta automáticamente."
                else
                    echo "- PostgreSQL del sistema: establece la clave para que coincida:"
                    echo "    sudo -u postgres psql -c \"ALTER USER postgres PASSWORD '$(cat /dev/null)SU_CLAVE_DE_.env.demo';\""
                    echo "  Sustituye SU_CLAVE_DE_.env.demo por el valor de DB_PASSWORD en .env.demo"
                    echo "  O edita .env.demo con la clave actual: nano .env.demo"
                fi
                ;;
            *"database"*"does not exist"*)
                echo "- La base '$db_name' no existe."
                echo "- Contenedor: podman exec alerta-postgres createdb -U postgres $db_name"
                echo "- Sistema:   sudo -u postgres createdb $db_name"
                ;;
            *"Connection refused"*|*"could not connect"*)
                echo "- No hay un PostgreSQL escuchando en $pg_host_tcp:$db_port."
                echo "- Contenedor: podman start alerta-postgres"
                echo "- Sistema:    sudo systemctl start postgresql"
                ;;
            *"no pg_hba.conf entry"*)
                echo "- PostgreSQL rechazó la conexión TCP por pg_hba.conf."
                echo "- Agrega: host all all 127.0.0.1/32 scram-sha-256"
                echo "- Ruta en Fedora: /var/lib/pgsql/data/pg_hba.conf"
                echo "- Luego: sudo systemctl reload postgresql"
                ;;
            *)
                echo "- Revise el mensaje anterior y target/logs/backend.log."
                ;;
        esac
    fi
fi
