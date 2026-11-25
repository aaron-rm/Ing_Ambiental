# Sistema de Alerta Temprana de Inundaciones

## 1. Qué es
Sistema de alerta temprana que combina el pronóstico hidrológico de caudal (GEOGloWS) y la precipitación prevista (Open-Meteo) para estimar niveles de riesgo ribereño, registrar eventos históricos y despachar notificaciones Web Push dirigidas a los usuarios suscritos a cada río.

> [!IMPORTANT]
> **Aviso:** Este es un proyecto académico y orientativo. **NO** emite declaraciones oficiales de emergencia ni sustituye la autoridad técnica ni los avisos del **SINAPROC** (Sistema Nacional de Protección Civil) ni del **IMHPA** (Instituto de Meteorología e Hidrología de Panamá).

---

## 2. Requisitos

- **Java Development Kit (JDK):** Versión 21 o superior.
- **Node.js y npm:** Node.js 20.19+ (probado con Node 24) y npm.
- **Docker Desktop:** Contenedor de base de datos PostgreSQL 16 con el nombre `alerta-postgres`, expuesto exclusivamente en `127.0.0.1:5432`.
  ```bash
  docker run -d --name alerta-postgres \
    -e POSTGRES_DB=alerta_inundaciones \
    -e POSTGRES_USER=postgres \
    -e POSTGRES_PASSWORD=su_contrasena_local \
    -p 127.0.0.1:5432:5432 \
    -v alerta-pgdata:/var/lib/postgresql/data \
    postgres:16-alpine
  ```
- **Git:** Para control de versiones y clonación del repositorio.

---

## 3. Configuración

Copia el archivo de plantilla a `.env.demo` (ignorado en Git):

En Windows:
```cmd
copy .env.demo.example .env.demo
```

En Linux:
```bash
cp .env.demo.example .env.demo
```

Edita `.env.demo` completando las variables según tu entorno local:

| Variable | Descripción | Valor de ejemplo / Placeholder |
|---|---|---|
| `DB_URL` | URL JDBC de conexión a PostgreSQL | `jdbc:postgresql://localhost:5432/alerta_inundaciones` |
| `DB_USERNAME` | Usuario de PostgreSQL | `postgres` |
| `DB_PASSWORD` | Contraseña de PostgreSQL (debe coincidir con la del contenedor) | `su_contrasena_local` |
| `ALERTA_JWT_SECRET` | Clave secreta para firma de tokens JWT (mínimo 32 caracteres) | `un-secreto-seguro-de-al-menos-32-caracteres` |
| `ALERTA_JWT_EXPIRACION_HORAS` | Duración del token JWT en horas | `24` |
| `ALERTA_ADMIN_IDENTIFICADOR` | Identificador de la cuenta administradora inicial | `admin` |
| `ALERTA_ADMIN_PASSWORD` | Contraseña del administrador local | `admin-local-clave` |
| `ALERTA_CORS_ORIGENES` | Orígenes HTTP/HTTPS autorizados para CORS | `http://localhost:3000,http://localhost:5173,http://localhost:8081` |
| `ALERTA_API_BASE_URL` | URL base consumida por la consola cliente | `http://localhost:8081` |
| `ALERTA_DEMO_CONSOLA_HABILITADA` | Habilita la consola interactiva CLI en el backend | `false` |
| `GEOGLOWS_CACHE_MINUTOS` | Tiempo de vida de la caché en memoria de GEOGloWS | `15` |
| `ALERTA_ADMIN_TOKEN` | Token para rutas administrativas heredadas en transición | `token-administrativo-opcional` |

---

## 4. Firebase (opcional, para notificaciones)

Para habilitar el envío de notificaciones Web Push, configura `FIREBASE_ENABLED=true` en `.env.demo`.

1. **Credenciales de cuenta de servicio:** Descarga la clave privada en formato JSON desde la consola de Firebase (**Configuración del proyecto → Cuentas de servicio**).
   - Guarda el archivo **FUERA del repositorio** (por ejemplo en `%USERPROFILE%\.secrets\ing-ambiental\service-account.json` en Windows o `~/.secrets/ing-ambiental/service-account.json` en Linux).
   - Define su ruta absoluta en `FIREBASE_CREDENTIALS_PATH`.

> [!WARNING]
> **Seguridad:** NUNCA subas el archivo JSON de la cuenta de servicio a Git ni lo sirvas al frontend web.

2. **Variables públicas de la aplicación web:** Copia los datos públicos de tu app web en Firebase Console:
   - `FIREBASE_API_KEY`: API Key web de Firebase.
   - `FIREBASE_AUTH_DOMAIN`: Dominio de autenticación (`proyecto.firebaseapp.com`).
   - `FIREBASE_PROJECT_ID`: ID del proyecto de Firebase.
   - `FIREBASE_MESSAGING_SENDER_ID`: ID del remitente de mensajería (Sender ID).
   - `FIREBASE_APP_ID`: ID de la aplicación web.
   - `FIREBASE_VAPID_KEY`: Clave pública Web Push (VAPID) generada en **Cloud Messaging**.

---

## 5. Iniciar

### Windows (Lanzador automatizado)
```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\iniciar-demo.ps1
```

### Linux (Lanzador automatizado)
```bash
bash iniciar.sh
```

**Qué levanta cada lanzador:**
- Backend Spring Boot en el puerto `8081`.
- Frontend React (Vite) en el puerto `5173`.
- Túnel HTTPS con Cloudflare hacia el puerto `5173` (si `cloudflared` está instalado), permitiendo abrir la aplicación en teléfonos móviles con contexto seguro para Web Push.

**Cómo detener:** Presiona `Ctrl + C` en la terminal para terminar limpiamente los procesos iniciados.

### Arranque manual alternativo
Si prefieres iniciar cada servicio por separado:

1. **Backend:**
   ```powershell
   .\mvnw.cmd spring-boot:run
   ```
   *(En Linux: `bash mvnw spring-boot:run`)*

2. **Frontend:**
   ```bash
   cd frontend
   npm install
   npm run dev
   ```

---

## 6. URLs y accesos

- **Aplicación Web (Frontend):** [http://localhost:5173](http://localhost:5173)
- **API REST (Backend):** [http://localhost:8081](http://localhost:8081)

### Credenciales de prueba local
- **Usuario:** `admin`
- **Contraseña:** `admin-local-clave`  
*(Uso exclusivo en desarrollo local; en producción debe cambiarse mediante `ALERTA_ADMIN_PASSWORD`).*

### Roles del sistema
- **USER:** Usuario general. Puede registrarse libremente desde la pantalla de login, explorar el mapa de ríos, suscribirse a ríos y recibir notificaciones push.
- **INVESTIGADOR:** Asignado por un `ADMIN` desde la vista Usuarios. Permite registrar mediciones ambientales y cargar muestras masivas mediante archivos CSV.
- **ADMIN:** Administrador del sistema. Puede eliminar ríos del catálogo, gestionar roles de usuarios en el sistema y ejecutar simulaciones de crecida.

---

## 7. Qué se puede hacer

- **Ver ríos sin cuenta:** Consultar el catálogo de ríos en el mapa interactivo, revisar los niveles de alerta actuales y los pronósticos de caudal y lluvia sin iniciar sesión.
- **Seguir ríos y recibir notificaciones:** Con una cuenta iniciada, suscribirse a uno o varios ríos y activar las notificaciones push en el navegador o teléfono móvil.
- **Agregar ríos por coordenadas:** Registrar nuevos ríos especificando nombre, latitud y longitud (manualmente, mediante la ubicación del dispositivo o haciendo clic directo en el mapa), asociando automáticamente el tramo hidrológico de GEOGloWS.
- **Registrar muestras y mediciones:** Usuarios con rol `ADMIN` o `INVESTIGADOR` pueden ingresar mediciones de calidad de agua o hidrológicas de forma individual o importar lotes masivos en formato CSV con la cabecera:
  ```csv
  fecha,parametro,valor,unidad,nota
  ```
- **Administración de catálogo y usuarios:** Los usuarios con rol `ADMIN` pueden eliminar ríos y modificar los roles de los usuarios registrados (`USER` o `INVESTIGADOR`).
- **Simular crecida:** El administrador puede disparar un simulacro de crecida sobre cualquier río para validar el despacho inmediato de notificaciones push a todos los dispositivos que sigan ese río.

---

## 8. Pruebas

### Pruebas del Backend
Ejecuta la suite de pruebas unitarias e integración en Spring Boot (82 pruebas automatizadas):
```powershell
.\mvnw.cmd test
```
*(En Linux: `bash mvnw test`)*

### Compilación del Frontend
Verifica que la aplicación React empaquete correctamente sin errores:
```bash
cd frontend
npm run build
```

---

## 9. Problemas comunes

- **GEOGloWS responde 429 (Too Many Requests):** La API pública de GEOGloWS aplica límites de peticiones concurrentes. El backend mitiga esto mediante una caché en memoria configurable (`GEOGLOWS_CACHE_MINUTOS`) y reutiliza la última respuesta válida en caso de fallo.
- **Puerto ocupado (`5173`, `8081` o `5432`):** Si un proceso anterior retiene el puerto, finalízalo antes de volver a arrancar.
- **Docker apagado:** Si el contenedor `alerta-postgres` no responde, asegúrate de que Docker Desktop esté encendido y ejecuta `docker start alerta-postgres`.
- **`mvnw` no se encuentra en PowerShell:** Si PowerShell no ejecuta el wrapper de Maven por restricciones de ruta relativa, ejecuta en la sesión:
  ```powershell
  $env:NoDefaultCurrentDirectoryInExePath = "1"
  ```
  o invoca explícitamente `.\mvnw.cmd`.

---

## 10. Estructura del repositorio

```text
├── src/          # Código fuente del Backend (Spring Boot), servicios, seguridad y recursos
├── frontend/     # Aplicación cliente React + Vite, componentes y páginas
├── scripts/      # Scripts de apoyo y lanzadores para Windows y Linux
├── docs/         # Documentación técnica, directrices de arquitectura y contratos
└── tools/        # Scripts auxiliares (generador reproducible de catálogo GeoJSON)
```

### Documentación técnica detallada
- [docs/ARQUITECTURA.md](docs/ARQUITECTURA.md) — Directrices permanentes de arquitectura y separación de componentes.
- [docs/CONFIGURACION_PUSH.md](docs/CONFIGURACION_PUSH.md) — Guía de configuración para Firebase Cloud Messaging (FCM) y base de datos.
- [docs/CONTINUIDAD_SESION.md](docs/CONTINUIDAD_SESION.md) — Historial de implementación, decisiones técnicas y estado actual.
- [docs/GUIA_FRONTEND.md](docs/GUIA_FRONTEND.md) — Especificaciones de interfaz y flujo de usuario.
- [docs/GUIA_PRESENTACION_FEDORA.md](docs/GUIA_PRESENTACION_FEDORA.md) — Guía paso a paso para la demostración en entornos Linux/Fedora.
- [docs/GUIA_PRUEBA_NOTIFICACIONES.md](docs/GUIA_PRUEBA_NOTIFICACIONES.md) — Protocolo de validación del canal push en dispositivos reales.
- [docs/INTEGRACION_FRONTEND.md](docs/INTEGRACION_FRONTEND.md) — Contrato completo de la API REST para el cliente frontend.
- [docs/RESUMEN_PROYECTO.md](docs/RESUMEN_PROYECTO.md) — Explicación de alto nivel del sistema y guía para exposición.
